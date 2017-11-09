/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.service.http.impl.service.client.async;

import static com.ning.http.client.AsyncHandler.STATE.ABORT;
import static com.ning.http.client.AsyncHandler.STATE.CONTINUE;
import org.mule.runtime.http.api.domain.message.response.HttpResponse;
import org.mule.service.http.impl.service.client.HttpResponseCreator;

import com.ning.http.client.AsyncHandler;
import com.ning.http.client.HttpResponseBodyPart;
import com.ning.http.client.HttpResponseHeaders;
import com.ning.http.client.HttpResponseStatus;
import com.ning.http.client.Response;

import java.io.IOException;
import java.io.InputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

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

  private static final Logger logger = LoggerFactory.getLogger(ResponseBodyDeferringAsyncHandler.class);

  private volatile Response response;
  private final int bufferSize;
  private PipedOutputStream output;
  private InputStream input;
  private final CompletableFuture<HttpResponse> future;
  private final Response.ResponseBuilder responseBuilder = new Response.ResponseBuilder();
  private final HttpResponseCreator httpResponseCreator = new HttpResponseCreator();
  private final AtomicBoolean handled = new AtomicBoolean(false);

  public ResponseBodyDeferringAsyncHandler(CompletableFuture<HttpResponse> future, int bufferSize) throws IOException {
    this.future = future;
    this.bufferSize = bufferSize;
  }

  @Override
  public void onThrowable(Throwable t) {
    try {
      closeOut();
    } catch (IOException e) {
      logger.debug("Error closing HTTP response stream", e);
    }
    if (!handled.getAndSet(true)) {
      Exception exception;
      if (t instanceof TimeoutException) {
        exception = (TimeoutException) t;
      } else if (t instanceof IOException) {
        exception = (IOException) t;
      } else {
        exception = new IOException(t);
      }
      future.completeExceptionally(exception);
    } else {
      if (t.getMessage() != null && t.getMessage().contains("Pipe closed")) {
        logger.warn("HTTP response stream was closed before being read but response streams must always be consumed.");
      } else {
        logger.warn("Error handling HTTP response stream. Set log level to DEBUG for details.");
      }
      logger.debug("HTTP response stream error was ", t);
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
    return CONTINUE;
  }

  @Override
  public STATE onBodyPartReceived(HttpResponseBodyPart bodyPart) throws Exception {
    if (input == null && bodyPart.isLast()) {
      responseBuilder.accumulate(bodyPart);
      handleIfNecessary();
      return CONTINUE;
    } else if (input == null) {
      output = new PipedOutputStream();
      input = new PipedInputStream(output, bufferSize);
    }
    // body arrived, can handle the partial response
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
        future.complete(httpResponseCreator.create(response, input != null ? input : response.getResponseBodyAsStream()));
      } catch (IOException e) {
        future.completeExceptionally(e);
      }
    }
  }
}
