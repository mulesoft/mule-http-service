/*
 * Copyright 2023 Salesforce, Inc. All rights reserved.
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.service.http.impl.service.client.async;

import static org.mule.runtime.core.api.util.UUID.getUUID;
import static org.mule.runtime.http.api.HttpHeaders.Names.CONTENT_LENGTH;
import static org.mule.runtime.http.api.HttpHeaders.Names.TRANSFER_ENCODING;
import static org.mule.service.http.impl.AllureConstants.HttpFeature.HTTP_SERVICE;
import static org.mule.service.http.impl.AllureConstants.HttpFeature.HttpStory.STREAMING;
import static org.mule.service.http.impl.service.client.async.ResponseBodyDeferringAsyncHandler.refreshSystemProperties;

import static java.lang.System.clearProperty;
import static java.lang.System.setProperty;
import static java.nio.ByteBuffer.allocateDirect;
import static java.nio.ByteBuffer.wrap;
import static java.util.concurrent.Executors.newFixedThreadPool;
import static java.util.concurrent.Executors.newSingleThreadExecutor;

import static com.ning.http.client.AsyncHandler.STATE.ABORT;
import static com.ning.http.client.AsyncHandler.STATE.CONTINUE;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.slf4j.MDC.getCopyOfContextMap;

import org.mule.runtime.api.util.Reference;
import org.mule.runtime.api.util.concurrent.Latch;
import org.mule.runtime.core.api.util.IOUtils;
import org.mule.runtime.http.api.domain.message.response.HttpResponse;
import org.mule.service.http.impl.service.client.NonBlockingStreamWriter;
import org.mule.service.http.impl.util.TimedPipedInputStream;
import org.mule.service.http.impl.util.TimedPipedOutputStream;
import org.mule.tck.junit4.AbstractMuleTestCase;
import org.mule.tck.probe.JUnitLambdaProbe;
import org.mule.tck.probe.PollingProber;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeoutException;

import com.ning.http.client.AsyncHandler;
import com.ning.http.client.FluentCaseInsensitiveStringsMap;
import com.ning.http.client.HttpResponseHeaders;
import com.ning.http.client.HttpResponseStatus;
import com.ning.http.client.providers.grizzly.GrizzlyResponseBodyPart;
import com.ning.http.client.providers.grizzly.PauseHandler;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Story;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.MDC;

@Feature(HTTP_SERVICE)
@Story(STREAMING)
public class ResponseBodyDeferringAsyncHandlerTestCase extends AbstractMuleTestCase {

  private static final int PROBE_TIMEOUT = 5000;
  private static final int POLL_DELAY = 300;
  private static final int BUFFER_SIZE = 1024;
  private final PauseHandler pauseHandler = mock(PauseHandler.class);

  private final ExecutorService testExecutor = newSingleThreadExecutor();
  private final PollingProber prober = new PollingProber(PROBE_TIMEOUT, POLL_DELAY);

  private final ExecutorService workersExecutor = newFixedThreadPool(5);
  private final NonBlockingStreamWriter nonBlockingStreamWriter = new NonBlockingStreamWriter();

  private static final String READ_TIMEOUT_PROPERTY_NAME = "mule.http.responseStreaming.pipeReadTimeoutMillis";

  @Before
  public void setup() {
    setProperty(READ_TIMEOUT_PROPERTY_NAME, "100");
    refreshSystemProperties();
  }

  @After
  public void tearDown() {
    clearProperty(READ_TIMEOUT_PROPERTY_NAME);
    refreshSystemProperties();
  }

  @Test
  public void doesNotStreamWhenPossible() throws Exception {
    CompletableFuture<HttpResponse> future = new CompletableFuture<>();
    Reference<InputStream> responseContent = new Reference<>();
    ResponseBodyDeferringAsyncHandler handler =
        new ResponseBodyDeferringAsyncHandler(future, BUFFER_SIZE, workersExecutor, nonBlockingStreamWriter);
    handler.onStatusReceived(mock(HttpResponseStatus.class, RETURNS_DEEP_STUBS));
    GrizzlyResponseBodyPart bodyPart = mockBodyPart(true, new byte[0]);
    future.whenComplete((response, exception) -> responseContent.set(response.getEntity().getContent()));

    assertThat(handler.onBodyPartReceived(bodyPart), is(CONTINUE));

    prober.check(new JUnitLambdaProbe(() -> {
      assertThat(responseContent.get(), not(instanceOf(TimedPipedInputStream.class)));
      return true;
    }));
  }

  @Test
  public void streamsWhenRequired() throws Exception {
    CompletableFuture<HttpResponse> future = new CompletableFuture<>();
    Reference<InputStream> responseContent = new Reference<>();
    ResponseBodyDeferringAsyncHandler handler =
        new ResponseBodyDeferringAsyncHandler(future, BUFFER_SIZE, workersExecutor, nonBlockingStreamWriter);
    handler.onStatusReceived(mock(HttpResponseStatus.class, RETURNS_DEEP_STUBS));
    GrizzlyResponseBodyPart bodyPart = mockBodyPart(false, new byte[0]);
    future.whenComplete((response, exception) -> responseContent.set(response.getEntity().getContent()));

    assertThat(handler.onBodyPartReceived(bodyPart), is(CONTINUE));

    prober.check(new JUnitLambdaProbe(() -> {
      assertThat(responseContent.get(), instanceOf(TimedPipedInputStream.class));
      return true;
    }));
  }

  @Test
  @Issue("MULE-19208")
  public void handlerAbortsResponseWhenAnErrorOccurred() throws Exception {
    CompletableFuture<HttpResponse> future = new CompletableFuture<>();
    ResponseBodyDeferringAsyncHandler handler =
        new ResponseBodyDeferringAsyncHandler(future, BUFFER_SIZE, workersExecutor, nonBlockingStreamWriter);
    GrizzlyResponseBodyPart bodyPart = mockBodyPart(false, new byte[0]);

    handler.onStatusReceived(mock(HttpResponseStatus.class, RETURNS_DEEP_STUBS));
    handler.onThrowable(new TimeoutException("Timeout exceeded"));
    AsyncHandler.STATE state = handler.onBodyPartReceived(bodyPart);
    assertThat(state, is(ABORT));

    prober.check(new JUnitLambdaProbe(future::isCompletedExceptionally));
  }

  @Test
  @Issue("MULE-19208")
  public void handlerDoesNotTryToWriteAPartIfAnErrorOccurred() throws Exception {
    CompletableFuture<HttpResponse> future = new CompletableFuture<>();
    ResponseBodyDeferringAsyncHandler handler =
        new ResponseBodyDeferringAsyncHandler(future, BUFFER_SIZE, workersExecutor, nonBlockingStreamWriter);
    GrizzlyResponseBodyPart bodyPart = mockBodyPart(false, new byte[0]);

    handler.onStatusReceived(mock(HttpResponseStatus.class, RETURNS_DEEP_STUBS));
    handler.onThrowable(new TimeoutException("Timeout exceeded"));
    handler.onBodyPartReceived(bodyPart);

    verify(bodyPart, never()).writeTo(any(TimedPipedOutputStream.class));
  }

  @Test
  @Issue("MULE-19208")
  public void handlerClosesPipedStreamIfAnErrorOccurredBetweenTwoParts() throws Exception {
    CompletableFuture<HttpResponse> future = new CompletableFuture<>();
    ResponseBodyDeferringAsyncHandler handler =
        new ResponseBodyDeferringAsyncHandler(future, BUFFER_SIZE, workersExecutor, nonBlockingStreamWriter);
    GrizzlyResponseBodyPart bodyPartBeforeError = mockBodyPart(false, new byte[0]);
    GrizzlyResponseBodyPart bodyPartAfterError = mockBodyPart(false, new byte[0]);

    handler.onStatusReceived(mock(HttpResponseStatus.class, RETURNS_DEEP_STUBS));
    assertThat(handler.onBodyPartReceived(bodyPartBeforeError), is(CONTINUE));
    handler.onThrowable(new TimeoutException("Timeout exceeded"));
    assertThat(handler.onBodyPartReceived(bodyPartAfterError), is(ABORT));

    prober.check(new JUnitLambdaProbe(() -> {
      // When the TimedPipedInputStream is closed by writer, read returns -1 indicating EOF.
      byte[] result = new byte[16];
      return future.get().getEntity().getContent().read(result) == -1;
    }));
  }

  @Test
  @Issue("MULE-19208")
  public void handlerDoesNotTryToWriteAPartIfAnErrorOccurredBetweenTwoParts() throws Exception {
    CompletableFuture<HttpResponse> future = new CompletableFuture<>();
    ResponseBodyDeferringAsyncHandler handler =
        new ResponseBodyDeferringAsyncHandler(future, BUFFER_SIZE, workersExecutor, nonBlockingStreamWriter);
    GrizzlyResponseBodyPart bodyPartBeforeError = mockBodyPart(false, new byte[0]);
    GrizzlyResponseBodyPart bodyPartAfterError = mockBodyPart(false, new byte[0]);

    handler.onStatusReceived(mock(HttpResponseStatus.class, RETURNS_DEEP_STUBS));
    assertThat(handler.onBodyPartReceived(bodyPartBeforeError), is(CONTINUE));
    handler.onThrowable(new TimeoutException("Timeout exceeded"));
    assertThat(handler.onBodyPartReceived(bodyPartAfterError), is(ABORT));

    verify(bodyPartBeforeError, times(1)).writeTo(any(TimedPipedOutputStream.class));
    verify(bodyPartAfterError, never()).writeTo(any(TimedPipedOutputStream.class));
  }

  @Test
  @Issue("MULE-19208")
  public void readerDoesNotBlockWhenNobodyWroteInTheStreamYet() throws Exception {
    CompletableFuture<HttpResponse> future = new CompletableFuture<>();
    ResponseBodyDeferringAsyncHandler handler =
        new ResponseBodyDeferringAsyncHandler(future, BUFFER_SIZE, workersExecutor, nonBlockingStreamWriter);
    GrizzlyResponseBodyPart bodyPart = mock(GrizzlyResponseBodyPart.class, RETURNS_DEEP_STUBS);
    when(bodyPart.isLast()).thenReturn(false);
    when(bodyPart.getBodyPartBytes()).thenReturn("payload".getBytes());
    when(bodyPart.getBodyByteBuffer()).thenReturn(allocateDirect(0));
    handler.onStatusReceived(mock(HttpResponseStatus.class, RETURNS_DEEP_STUBS));
    Latch writeLatch = new Latch();
    doAnswer(invocation -> {
      writeLatch.await();
      return invocation.callRealMethod();
    }).when(bodyPart).writeTo(any(TimedPipedOutputStream.class));

    testExecutor.submit(() -> {
      try {
        assertThat(handler.onBodyPartReceived(bodyPart), is(CONTINUE));
      } catch (Exception e) {
        fail(e.getMessage());
      }
    });

    // The read operation isn't blocking when nobody wrote in the stream.
    prober.check(new JUnitLambdaProbe(() -> {
      // When the TimedPipedInputStream was never written and it's still open, read returns 0.
      byte[] result = new byte[16];
      int bytesRead = future.get().getEntity().getContent().read(result);
      return bytesRead == 0;
    }));

    writeLatch.release();

    assertThat(handler.onBodyPartReceived(bodyPart), is(CONTINUE));
    handler.onCompleted();

    // The read operation returns EOF when the pipe was closed while it was empty.
    prober.check(new JUnitLambdaProbe(() -> {
      // When the TimedPipedInputStream is closed by writer, read returns -1 indicating EOF.
      byte[] result = new byte[16];
      return future.get().getEntity().getContent().read(result) == -1;
    }));
  }

  @Test
  public void abortsWhenPipeIsClosed() throws Exception {
    CompletableFuture<HttpResponse> future = new CompletableFuture<>();
    ResponseBodyDeferringAsyncHandler handler =
        new ResponseBodyDeferringAsyncHandler(future, BUFFER_SIZE, workersExecutor, nonBlockingStreamWriter);
    handler.onStatusReceived(mock(HttpResponseStatus.class, RETURNS_DEEP_STUBS));
    GrizzlyResponseBodyPart bodyPart =
        mockBodyPart(false, "You will call me Snowball because my fur is pretty and white.".getBytes());
    handler.onBodyPartReceived(bodyPart);
    handler.closeOut();
    assertThat(handler.onBodyPartReceived(bodyPart), is(ABORT));
  }

  @Test
  public void doesNotThrowExceptionIfContentLengthIsGreaterThanMaxInteger() throws Exception {
    CompletableFuture<HttpResponse> future = new CompletableFuture<>();
    ResponseBodyDeferringAsyncHandler handler =
        new ResponseBodyDeferringAsyncHandler(future, -1, workersExecutor, nonBlockingStreamWriter);
    handler.onStatusReceived(mock(HttpResponseStatus.class, RETURNS_DEEP_STUBS));

    FluentCaseInsensitiveStringsMap headersMap = mock((FluentCaseInsensitiveStringsMap.class));
    when(headersMap.getFirstValue(CONTENT_LENGTH)).thenReturn(Long.toString((long) Integer.MAX_VALUE * 2));
    when(headersMap.getFirstValue(TRANSFER_ENCODING)).thenReturn("");

    HttpResponseHeaders headers = mock(HttpResponseHeaders.class);
    when(headers.getHeaders()).thenReturn(headersMap);

    assertThat(handler.onHeadersReceived(headers), is(CONTINUE));
  }

  @Test
  @Issue("W-16640190")
  public void readFromPipeInWhenCompleteDoesNotCauseADeadlock() throws Exception {
    CompletableFuture<HttpResponse> future = new CompletableFuture<>();
    Reference<String> responseContent = new Reference<>();
    ResponseBodyDeferringAsyncHandler handler =
        new ResponseBodyDeferringAsyncHandler(future, BUFFER_SIZE, workersExecutor, nonBlockingStreamWriter);
    handler.onStatusReceived(mock(HttpResponseStatus.class, RETURNS_DEEP_STUBS));

    GrizzlyResponseBodyPart intermediatePart = mockBodyPart(false, "Hello ".getBytes());
    GrizzlyResponseBodyPart lastPart = mockBodyPart(true, "world".getBytes());

    future.whenComplete((response, exception) -> {
      String responseAsString = IOUtils.toString(response.getEntity().getContent());
      responseContent.set(responseAsString);
    });

    assertThat(handler.onBodyPartReceived(intermediatePart), is(CONTINUE));
    assertThat(handler.onBodyPartReceived(lastPart), is(CONTINUE));
    assertThat(handler.onCompleted(), is(nullValue()));

    prober.check(new JUnitLambdaProbe(() -> {
      assertThat(responseContent.get(), is("Hello world"));
      return true;
    }));
  }

  @Test
  @Issue("W-17048606")
  public void writePartBiggerThanBufferResultsInAsyncWrite() throws Exception {
    // use default timeout for this test
    clearProperty(READ_TIMEOUT_PROPERTY_NAME);
    refreshSystemProperties();

    int smallBufferSize = 5;

    CompletableFuture<HttpResponse> future = new CompletableFuture<>();
    ResponseBodyDeferringAsyncHandler handler =
        new ResponseBodyDeferringAsyncHandler(future, smallBufferSize, workersExecutor, nonBlockingStreamWriter);

    GrizzlyResponseBodyPart intermediatePart = mockBodyPart(false, "Hello ".getBytes());
    GrizzlyResponseBodyPart lastPart = mockBodyPart(true, "world".getBytes());

    assertThat(handler.onStatusReceived(mock(HttpResponseStatus.class, RETURNS_DEEP_STUBS)), is(CONTINUE));
    assertThat(handler.onBodyPartReceived(intermediatePart), is(CONTINUE));

    // The part doesn't fit into the buffer, then we pause the event processing
    verify(pauseHandler).requestPause();

    // The (incomplete) response can be gotten from the future, and it has a full pipe (available = buffer size)
    HttpResponse response = future.get();
    InputStream pipe = response.getEntity().getContent();
    assertThat(pipe, instanceOf(TimedPipedInputStream.class));
    assertThat(pipe.available(), is(smallBufferSize));

    // Not resumed yet, because the async writer isn't running
    verify(pauseHandler, never()).resume();

    // Now we run the writer, consume the pipe async, and write the last part
    StringBuilder responseAsString = new StringBuilder();
    testExecutor.submit(() -> consumePipe(pipe, responseAsString));
    workersExecutor.submit(nonBlockingStreamWriter);

    // Now that the writer is running, the event processing has to be resumed at least once
    prober.check(new JUnitLambdaProbe(() -> {
      verify(pauseHandler, atLeastOnce()).resume();
      return true;
    }));

    // And now that the write is resumed, we can simulate the receiving of the last part
    assertThat(handler.onBodyPartReceived(lastPart), is(CONTINUE));

    // Eventually, the whole response can be consumed from the pipe
    prober.check(new JUnitLambdaProbe(() -> {
      synchronized (responseAsString) {
        assertThat(responseAsString.toString(), is("Hello world"));
      }
      return true;
    }));

    // Receive the onComplete...
    assertThat(handler.onCompleted(), is(nullValue()));
    nonBlockingStreamWriter.stop();
  }

  @Test
  @Issue("W-17048606")
  public void asyncWriteHappensWithSameTCCL() throws Exception {
    // use default timeout for this test
    clearProperty(READ_TIMEOUT_PROPERTY_NAME);
    refreshSystemProperties();
    int smallBufferSize = 5;

    final String randomKey = getUUID();
    MDC.put(randomKey, "TestValue");
    final Map<String, String> mdcSeenOnThrowable = new HashMap<>();

    CompletableFuture<HttpResponse> future = new CompletableFuture<>();
    ResponseBodyDeferringAsyncHandler handler =
        new ResponseBodyDeferringAsyncHandler(future, smallBufferSize, workersExecutor, nonBlockingStreamWriter) {

          @Override
          public void onThrowable(Throwable t) {
            // Save the MDC to make assertions later
            mdcSeenOnThrowable.putAll(getCopyOfContextMap());
            super.onThrowable(t);
          }
        };

    GrizzlyResponseBodyPart nonLastPart = mockBodyPart(false, "Hello ".getBytes());

    assertThat(handler.onStatusReceived(mock(HttpResponseStatus.class, RETURNS_DEEP_STUBS)), is(CONTINUE));
    assertThat(handler.onBodyPartReceived(nonLastPart), is(CONTINUE));

    HttpResponse response = future.get();
    response.getEntity().getContent().close();

    workersExecutor.submit(nonBlockingStreamWriter);

    // As we closed the pipe, the write operation has to throw an error and the onThrowable has to be called in the
    // NonBlockingWriter's thread. The onThrowable should be called with the MDC that was present when we created the
    // asyncHandler.
    prober.check(new JUnitLambdaProbe(() -> {
      assertThat(mdcSeenOnThrowable.get(randomKey), is("TestValue"));
      return true;
    }));

    MDC.remove(randomKey);
  }

  private static void consumePipe(InputStream pipe, StringBuilder responseStringBuilder) {
    boolean keepReading = true;
    while (keepReading) {
      try {
        int b = pipe.read();
        if (b == -1) {
          keepReading = false;
        } else {
          synchronized (responseStringBuilder) {
            responseStringBuilder.append((char) b);
          }
        }
      } catch (IOException e) {
        fail("Got exception reading from pipe");
      }
    }
  }

  private GrizzlyResponseBodyPart mockBodyPart(boolean isLast, byte[] content) throws IOException {
    GrizzlyResponseBodyPart bodyPart = mock(GrizzlyResponseBodyPart.class, RETURNS_DEEP_STUBS);
    when(bodyPart.isLast()).thenReturn(isLast);
    when(bodyPart.getBodyByteBuffer()).thenReturn(wrap(content));
    when(bodyPart.getBodyPartBytes()).thenReturn(content);
    when(bodyPart.length()).thenReturn(content.length);
    when(bodyPart.getPauseHandler()).thenReturn(pauseHandler);
    doAnswer(invocation -> {
      OutputStream outputStream = invocation.getArgument(0);
      outputStream.write(content);
      return content.length;
    }).when(bodyPart).writeTo(any(OutputStream.class));
    return bodyPart;
  }
}
