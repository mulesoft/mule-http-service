/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.service.http.impl.service.client.async;

import static com.ning.http.client.AsyncHandler.STATE.ABORT;
import static com.ning.http.client.AsyncHandler.STATE.CONTINUE;
import static java.lang.Integer.valueOf;
import static java.lang.Math.min;
import static java.util.Optional.empty;
import static java.util.Optional.of;
import static org.apache.commons.lang3.StringUtils.isEmpty;
import static org.glassfish.grizzly.nio.transport.TCPNIOTransport.MAX_RECEIVE_BUFFER_SIZE;
import static org.mule.runtime.api.util.DataUnit.KB;
import static org.mule.runtime.http.api.HttpHeaders.Names.CONTENT_LENGTH;
import static org.mule.runtime.http.api.HttpHeaders.Names.TRANSFER_ENCODING;
import org.mule.runtime.http.api.domain.message.response.HttpResponse;
import org.mule.service.http.impl.service.client.HttpResponseCreator;

import com.ning.http.client.AsyncHandler;
import com.ning.http.client.HttpResponseBodyPart;
import com.ning.http.client.HttpResponseHeaders;
import com.ning.http.client.HttpResponseStatus;
import com.ning.http.client.Response;
import com.ning.http.client.providers.grizzly.GrizzlyResponseHeaders;

import java.io.IOException;
import java.io.InputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.lang.reflect.Field;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

import org.glassfish.grizzly.http.HttpResponsePacket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Non blocking async handler which uses a {@link PipedOutputStream} to populate the HTTP response as it arrives, propagating an
 * {@link PipedInputStream} as soon as the response headers are parsed.
 * <p/>
 * Because of the internal buffer used to hold the arriving chunks, the response MUST be eventually read or the worker threads
 * will block waiting to allocate them. Likewise, read/write speed differences could cause issues. The buffer size can be
 * customized for these reason.
 * <p/>
 * To avoid deadlocks, a hand off to another thread MUST be performed before consuming the response.
 *
 * @since 1.0
 */
public class ResponseBodyDeferringAsyncHandler implements AsyncHandler<Response> {

  private static final Logger LOGGER = LoggerFactory.getLogger(ResponseBodyDeferringAsyncHandler.class);
  private static Field responseField;

  private volatile Response response;
  private int bufferSize;
  private PipedOutputStream output;
  private Optional<InputStream> input = empty();
  private final CompletableFuture<HttpResponse> future;
  private final Response.ResponseBuilder responseBuilder = new Response.ResponseBuilder();
  private final HttpResponseCreator httpResponseCreator = new HttpResponseCreator();
  private final AtomicBoolean handled = new AtomicBoolean(false);

  static {
    try {
      responseField = GrizzlyResponseHeaders.class.getDeclaredField("response");
      responseField.setAccessible(true);
    } catch (Throwable e) {
      LOGGER.warn("Unable to use reflection to access connection buffer size to optimize streaming.", e);
    }
  }

  public ResponseBodyDeferringAsyncHandler(CompletableFuture<HttpResponse> future, int userDefinedBufferSize) throws IOException {
    this.future = future;
    this.bufferSize = userDefinedBufferSize;
  }

  @Override
  public void onThrowable(Throwable t) {
    try {
      closeOut();
    } catch (IOException e) {
      LOGGER.debug("Error closing HTTP response stream", e);
    }
    if (!handled.getAndSet(true)) {
      Exception exception;
      if (t instanceof TimeoutException) {
        exception = (TimeoutException) t;
      } else if (t instanceof IOException) {
        exception = (IOException) t;
      } else {
        exception = new IOException(t.getMessage(), t);
      }
      future.completeExceptionally(exception);
    } else {
      if (t.getMessage() != null && t.getMessage().contains("Pipe closed")) {
        LOGGER.warn("HTTP response stream was closed before being read but response streams must always be consumed.");
      } else {
        LOGGER.warn("Error handling HTTP response stream. Set log level to DEBUG for details.");
      }
      LOGGER.debug("HTTP response stream error was ", t);
    }
  }

  @Override
  public STATE onStatusReceived(HttpResponseStatus responseStatus) throws Exception {
    responseBuilder.reset();
    responseBuilder.accumulate(responseStatus);
    return CONTINUE;
  }

  @Override
  public STATE onHeadersReceived(HttpResponseHeaders headers) throws Exception {
    responseBuilder.accumulate(headers);
    if (bufferSize < 0) {
      // If user hasn't configured a buffer size (default) then calculate it.
      LOGGER.debug("onHeadersReceived. No configured buffer size, resolving buffer size dynamically.");
      calculateBufferSize(headers);
    } else {
      LOGGER.debug("onHeadersReceived. Using user configured buffer size of '{} bytes'.", bufferSize);
    }
    return CONTINUE;
  }

  /**
   * Attempts to optimize the buffer size based on the presence of the content length header, the connection buffer size and the
   * maximum buffer size possible. Defaults to an ~32KB buffer when transfer encoding is used.
   *
   * @param headers the current headers received
   * @throws IllegalAccessException
   */
  private void calculateBufferSize(HttpResponseHeaders headers) {
    int maxBufferSize = MAX_RECEIVE_BUFFER_SIZE;
    String contentLength = headers.getHeaders().getFirstValue(CONTENT_LENGTH);
    if (!isEmpty(contentLength) && isEmpty(headers.getHeaders().getFirstValue(TRANSFER_ENCODING))) {
      int contentLengthInt = valueOf(contentLength);
      try {
        if (responseField != null && headers instanceof GrizzlyResponseHeaders) {
          maxBufferSize = (((HttpResponsePacket) responseField.get(headers)).getRequest().getConnection().getReadBufferSize());
        }
      } catch (IllegalAccessException e) {
        LOGGER.debug("Unable to access connection buffer size.");
      }
      bufferSize = min(maxBufferSize, contentLengthInt);
    } else {
      // Assume maximum 32Kb chunk size + 10 bytes for chunk size and new lines etc. (need to confirm is this is needed, but use
      // for now)
      bufferSize = KB.toBytes(32) + 10;
    }
    LOGGER
        .debug("Max buffer size = {} bytes, Connection buffer size = {} bytes, Content-length = {} bytes, Calculated buffer size = {} bytes",
               MAX_RECEIVE_BUFFER_SIZE, maxBufferSize, contentLength, bufferSize);
  }

  @Override
  public STATE onBodyPartReceived(HttpResponseBodyPart bodyPart) throws Exception {
    // body arrived, can handle the partial response
    if (!input.isPresent()) {
      if (bodyPart.isLast()) {
        // no need to stream response, we already have it all
        if (LOGGER.isDebugEnabled()) {
          LOGGER.debug("Single part (size = {}bytes).", bodyPart.getBodyByteBuffer().remaining());
        }
        responseBuilder.accumulate(bodyPart);
        handleIfNecessary();
        return CONTINUE;
      } else {
        output = new PipedOutputStream();
        input = of(new PipedInputStream(output, bufferSize));
      }
    }
    if (LOGGER.isDebugEnabled()) {
      int bodyLength = bodyPart.getBodyByteBuffer().remaining();
      LOGGER.debug("Multiple parts (part size = {} bytes, PipedInputStream buffer size = {} bytes).", bodyLength, bufferSize);
      if (bufferSize - input.get().available() < bodyLength) {
        //TODO - MULE-10550: Process to detect blocking of non-io threads should take care of this
        LOGGER
            .debug("SELECTOR BLOCKED! No room in piped stream to write {} bytes immediately. There are still has {} bytes unread",
                   LOGGER, input.get().available());
      }
    }
    handleIfNecessary();
    try {
      bodyPart.writeTo(output);
    } catch (IOException e) {
      this.onThrowable(e);
      return ABORT;
    }
    return CONTINUE;
  }

  protected void closeOut() throws IOException {
    if (output != null) {
      try {
        output.flush();
      } finally {
        output.close();
      }
    }
  }

  @Override
  public Response onCompleted() throws IOException {
    // there may have been no body, handle partial response
    handleIfNecessary();
    closeOut();
    return null;
  }

  private void handleIfNecessary() {
    if (!handled.getAndSet(true)) {
      response = responseBuilder.build();
      try {
        future.complete(httpResponseCreator.create(response, input.orElse(response.getResponseBodyAsStream())));
      } catch (IOException e) {
        future.completeExceptionally(e);
      }
    }
  }
}
