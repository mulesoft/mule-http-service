/*
 * Copyright 2023 Salesforce, Inc. All rights reserved.
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.service.http.impl.service.client.async;

import static org.mule.runtime.api.util.DataUnit.KB;
import static org.mule.runtime.api.util.MuleSystemProperties.SYSTEM_PROPERTY_PREFIX;
import static org.mule.runtime.http.api.HttpHeaders.Names.CONTENT_LENGTH;
import static org.mule.runtime.http.api.HttpHeaders.Names.TRANSFER_ENCODING;

import static java.lang.Integer.parseInt;
import static java.lang.Long.parseLong;
import static java.lang.Math.min;
import static java.lang.System.getProperty;
import static java.util.Optional.empty;
import static java.util.Optional.of;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

import static com.ning.http.client.AsyncHandler.STATE.ABORT;
import static com.ning.http.client.AsyncHandler.STATE.CONTINUE;
import static org.apache.commons.lang3.StringUtils.isEmpty;
import static org.glassfish.grizzly.nio.transport.TCPNIOTransport.MAX_RECEIVE_BUFFER_SIZE;

import org.mule.runtime.http.api.domain.message.response.HttpResponse;
import org.mule.service.http.impl.service.client.HttpResponseCreator;
import org.mule.service.http.impl.service.client.NonBlockingStreamWriter;
import org.mule.service.http.impl.service.client.sse.NoOpProgressiveBodyDataListener;
import org.mule.service.http.impl.service.client.sse.ProgressiveBodyDataListener;
import org.mule.service.http.impl.service.util.ThreadContext;
import org.mule.service.http.impl.util.TimedPipedInputStream;
import org.mule.service.http.impl.util.TimedPipedOutputStream;

import java.io.IOException;
import java.io.InputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.lang.reflect.Field;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;

import com.ning.http.client.AsyncHandler;
import com.ning.http.client.HttpResponseBodyPart;
import com.ning.http.client.HttpResponseHeaders;
import com.ning.http.client.HttpResponseStatus;
import com.ning.http.client.Response;
import com.ning.http.client.providers.grizzly.GrizzlyResponseHeaders;
import com.ning.http.client.providers.grizzly.PauseHandler;
import org.glassfish.grizzly.http.HttpResponsePacket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

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
  private static final String PIPE_READ_TIMEOUT_PROPERTY_NAME =
      SYSTEM_PROPERTY_PREFIX + "http.responseStreaming.pipeReadTimeoutMillis";
  private static long PIPE_READ_TIMEOUT_MILLIS = parseInt(getProperty(PIPE_READ_TIMEOUT_PROPERTY_NAME, "20000"));
  private static Field responseField;

  private volatile Response response;
  private int bufferSize;
  private final NonBlockingStreamWriter nonBlockingStreamWriter;
  private final ProgressiveBodyDataListener dataListener;
  private final ExecutorService workerScheduler;
  private TimedPipedOutputStream output;
  private Optional<TimedPipedInputStream> input = empty();
  private final CompletableFuture<HttpResponse> future;
  private final Response.ResponseBuilder responseBuilder = new Response.ResponseBuilder();
  private final HttpResponseCreator httpResponseCreator = new HttpResponseCreator();
  private final AtomicBoolean handled = new AtomicBoolean(false);
  private final Map<String, String> mdc;

  static {
    try {
      responseField = GrizzlyResponseHeaders.class.getDeclaredField("response");
      responseField.setAccessible(true);
    } catch (Throwable e) {
      LOGGER.warn("Unable to use reflection to access connection buffer size to optimize streaming.", e);
    }
  }

  private final AtomicReference<Throwable> throwableReceived = new AtomicReference<>();
  private final AtomicBoolean lastPartReceived = new AtomicBoolean(false);

  // TODO: Remove this constructor...
  public ResponseBodyDeferringAsyncHandler(CompletableFuture<HttpResponse> future, int userDefinedBufferSize,
                                           ExecutorService workerScheduler,
                                           NonBlockingStreamWriter nonBlockingStreamWriter) {
    this(future, userDefinedBufferSize, workerScheduler, nonBlockingStreamWriter, new NoOpProgressiveBodyDataListener());
  }

  public ResponseBodyDeferringAsyncHandler(CompletableFuture<HttpResponse> future, int userDefinedBufferSize,
                                           ExecutorService workerScheduler,
                                           NonBlockingStreamWriter nonBlockingStreamWriter,
                                           ProgressiveBodyDataListener dataListener) {
    this.future = future;
    this.bufferSize = userDefinedBufferSize;
    this.workerScheduler = workerScheduler;
    this.nonBlockingStreamWriter = nonBlockingStreamWriter;
    this.dataListener = dataListener;
    this.mdc = MDC.getCopyOfContextMap();
  }

  @Override
  public void onThrowable(Throwable t) {
    throwableReceived.set(t);
    try {
      MDC.setContextMap(mdc);
      LOGGER.debug("Error caught handling response body", t);
      try {
        cancelOut(t);
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
          // This class is reading the content of the response, and writing it to an internal stream. Reaching this code means
          // that the internal stream is closed, and we're reading more data. This may cause some truncated response payload in
          // the reader side of the internal stream.
          LOGGER
              .error("HTTP response stream was closed before being read but response streams must always be consumed. Set log level to DEBUG for details.");
        } else {
          LOGGER.warn("Error handling HTTP response stream. Set log level to DEBUG for details.");
        }
        LOGGER.debug("HTTP response stream error was ", t);
      }
    } finally {
      MDC.clear();
    }
  }

  private void cancelOut(Throwable t) throws IOException {
    if (output != null) {
      try {
        output.flush();
      } finally {
        output.cancel(t);
      }
    }
  }

  @Override
  public STATE onStatusReceived(HttpResponseStatus responseStatus) throws Exception {
    try {
      MDC.setContextMap(mdc);
      if (errorDetected()) {
        return closeAndAbort();
      }
      responseBuilder.reset();
      responseBuilder.accumulate(responseStatus);
      return CONTINUE;
    } finally {
      MDC.clear();
    }
  }

  private STATE closeAndAbort() throws IOException {
    if (throwableReceived.get() != null) {
      cancelOut(throwableReceived.get());
    } else {
      closeOut();
    }
    return ABORT;
  }

  @Override
  public STATE onHeadersReceived(HttpResponseHeaders headers) throws Exception {
    try {
      MDC.setContextMap(mdc);
      if (errorDetected()) {
        return closeAndAbort();
      }
      responseBuilder.accumulate(headers);
      if (bufferSize < 0) {
        // If user hasn't configured a buffer size (default) then calculate it.
        LOGGER.debug("onHeadersReceived. No configured buffer size, resolving buffer size dynamically.");
        calculateBufferSize(headers);
      } else {
        LOGGER.debug("onHeadersReceived. Using user configured buffer size of '{} bytes'.", bufferSize);
      }
      return CONTINUE;
    } finally {
      MDC.clear();
    }
  }

  /**
   * Attempts to optimize the buffer size based on the presence of the content length header, the connection buffer size and the
   * maximum buffer size possible. Defaults to an ~32KB buffer when transfer encoding is used.
   *
   * @param headers the current headers received
   */
  private void calculateBufferSize(HttpResponseHeaders headers) {
    int maxBufferSize = MAX_RECEIVE_BUFFER_SIZE;
    String contentLength = headers.getHeaders().getFirstValue(CONTENT_LENGTH);
    if (!isEmpty(contentLength) && isEmpty(headers.getHeaders().getFirstValue(TRANSFER_ENCODING))) {
      long contentLengthLong = parseLong(contentLength);
      try {
        if (responseField != null && headers instanceof GrizzlyResponseHeaders) {
          maxBufferSize = (((HttpResponsePacket) responseField.get(headers)).getRequest().getConnection().getReadBufferSize());
        }
      } catch (IllegalAccessException e) {
        LOGGER.debug("Unable to access connection buffer size.");
      }
      // The min result can be safely casted because maxBufferSize is an integer.
      bufferSize = (int) min(maxBufferSize, contentLengthLong);
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
    try {
      MDC.setContextMap(mdc);
      if (bodyPart.isLast()) {
        lastPartReceived.set(true);
      }
      if (errorDetected()) {
        return closeAndAbort();
      }
      if (!input.isPresent()) {
        if (bodyPart.isLast()) {
          // no need to stream response, we already have it all
          if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Single part (size = {} bytes).", bodyPart.getBodyByteBuffer().remaining());
          }
          responseBuilder.accumulate(bodyPart);
          handleIfNecessary();
          dataListener.onDataAvailable(bodyPart.length());
          dataListener.onEndOfStream();
          return CONTINUE;
        } else {
          output = new TimedPipedOutputStream();
          input = of(new TimedPipedInputStream(bufferSize, PIPE_READ_TIMEOUT_MILLIS, MILLISECONDS, output, () -> {
            if (null != nonBlockingStreamWriter) {
              nonBlockingStreamWriter.notifyAvailableSpace();
            }
          }));
        }
      }
      handleIfNecessary();
      if (errorDetected()) {
        return closeAndAbort();
      }
      try {
        return writeBodyPartToPipe(bodyPart);
      } catch (IOException e) {
        this.onThrowable(e);
        return ABORT;
      }
    } finally {
      MDC.clear();
    }
  }

  private STATE writeBodyPartToPipe(HttpResponseBodyPart bodyPart) throws IOException {
    int bodyLength = bodyPart.length();
    int spaceInPipe = availableSpaceInPipe();
    if (nonBlockingStreamWriter.isEnabled() && spaceInPipe >= 0 && spaceInPipe < bodyLength) {
      // There is no room to write everything, so we defer the content writing to the output stream. Also, to avoid
      // receiving more bodyParts temporarily, we have to pause the READ events.
      final PauseHandler pauseHandler = bodyPart.getPauseHandler();
      pauseHandler.requestPause();
      nonBlockingStreamWriter
          .addDataToWrite(output, bodyPart.getBodyPartBytes(), this::availableSpaceInPipe)
          .whenComplete(resumeCallback(pauseHandler, bodyPart));
    } else {
      bodyPart.writeTo(output);
      dataListener.onDataAvailable(bodyLength);
      if (bodyPart.isLast()) {
        closeOut();
        dataListener.onEndOfStream();
      }
    }
    return CONTINUE;
  }

  private BiConsumer<Void, Throwable> resumeCallback(final PauseHandler pauseHandler, HttpResponseBodyPart bodyPart) {
    return (ignored, error) -> {
      if (error != null) {
        onThrowable(error);
      }
      try {
        dataListener.onDataAvailable(bodyPart.length());
        if (bodyPart.isLast()) {
          closeOut();
          dataListener.onEndOfStream();
        }
        pauseHandler.resume();
      } catch (Exception e) {
        onThrowable(e);
      }
    };
  }

  private int availableSpaceInPipe() {
    if (!input.isPresent()) {
      return -1;
    }
    if (input.get().isClosed()) {
      return -1;
    }
    return bufferSize - input.get().available();
  }

  private boolean errorDetected() {
    return future.isCompletedExceptionally() || throwableReceived.get() != null;
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
    try {
      MDC.setContextMap(mdc);
      LOGGER.debug("Completed response");
      // there may have been no body, handle partial response
      handleIfNecessary();
      if (!lastPartReceived.get()) {
        // If the last part was not received yet, it won't be received. It's because AHC doesn't call the onBodyPartReceived for
        // the last part if it's empty. In that case, we need to close the pipe here.
        closeOut();
        dataListener.onEndOfStream();
      }
      return null;
    } finally {
      MDC.clear();
    }
  }

  private void handleIfNecessary() {
    if (!handled.getAndSet(true)) {
      if (shouldCompleteAsync()) {
        try {
          LOGGER.debug("Scheduling response future completion to workers scheduler");
          ClassLoader outerTccl = Thread.currentThread().getContextClassLoader();
          workerScheduler.submit(() -> {
            try (ThreadContext ctx = new ThreadContext(outerTccl, mdc)) {
              completeResponseFuture();
            }
          });
        } catch (RejectedExecutionException e) {
          LOGGER.warn("Couldn't schedule completion to workers scheduler, completing it synchronously");
          completeResponseFuture();
        }
      } else {
        completeResponseFuture();
      }
    }
  }

  private boolean shouldCompleteAsync() {
    return input.isPresent();
  }

  private void completeResponseFuture() {
    response = responseBuilder.build();
    try {
      InputStream is = createResponseInputStream();
      dataListener.onStreamCreated(is);
      future.complete(httpResponseCreator.create(response, is));
    } catch (IOException e) {
      // Make sure all resources are accounted for and since we've set the handled flag, handle the future explicitly
      onThrowable(e);
      future.completeExceptionally(e);
    }
  }

  private InputStream createResponseInputStream() throws IOException {
    if (input.isPresent()) {
      return input.get();
    } else {
      return response.getResponseBodyAsStream();
    }
  }

  /**
   * @deprecated Used only for testing
   */
  @Deprecated
  static void refreshSystemProperties() {
    PIPE_READ_TIMEOUT_MILLIS = parseInt(getProperty(PIPE_READ_TIMEOUT_PROPERTY_NAME, "20000"));
  }
}
