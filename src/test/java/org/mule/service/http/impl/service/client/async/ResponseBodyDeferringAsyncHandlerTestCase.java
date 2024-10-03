/*
 * Copyright 2023 Salesforce, Inc. All rights reserved.
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.service.http.impl.service.client.async;

import static com.ning.http.client.AsyncHandler.STATE.ABORT;
import static com.ning.http.client.AsyncHandler.STATE.CONTINUE;
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
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.mule.runtime.api.util.Reference;
import org.mule.runtime.api.util.concurrent.Latch;
import org.mule.runtime.core.api.util.IOUtils;
import org.mule.runtime.http.api.domain.message.response.HttpResponse;
import org.mule.service.http.impl.util.TimedPipedInputStream;
import org.mule.service.http.impl.util.TimedPipedOutputStream;
import org.mule.tck.junit4.AbstractMuleTestCase;
import org.mule.tck.probe.JUnitLambdaProbe;
import org.mule.tck.probe.PollingProber;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeoutException;

import com.ning.http.client.AsyncHandler;
import com.ning.http.client.FluentCaseInsensitiveStringsMap;
import com.ning.http.client.HttpResponseHeaders;
import com.ning.http.client.HttpResponseStatus;
import com.ning.http.client.providers.grizzly.GrizzlyResponseBodyPart;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Story;
import org.glassfish.grizzly.Connection;
import org.glassfish.grizzly.http.HttpContent;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

@Feature(HTTP_SERVICE)
@Story(STREAMING)
public class ResponseBodyDeferringAsyncHandlerTestCase extends AbstractMuleTestCase {

  private static final int PROBE_TIMEOUT = 5000;
  private static final int POLL_DELAY = 300;
  private static final int BUFFER_SIZE = 1024;

  private final ExecutorService testExecutor = newSingleThreadExecutor();
  private final PollingProber prober = new PollingProber(PROBE_TIMEOUT, POLL_DELAY);

  private final ExecutorService workersExecutor = newFixedThreadPool(5);

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
    ResponseBodyDeferringAsyncHandler handler = new ResponseBodyDeferringAsyncHandler(future, BUFFER_SIZE, workersExecutor);
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
    ResponseBodyDeferringAsyncHandler handler = new ResponseBodyDeferringAsyncHandler(future, BUFFER_SIZE, workersExecutor);
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
    ResponseBodyDeferringAsyncHandler handler = new ResponseBodyDeferringAsyncHandler(future, BUFFER_SIZE, workersExecutor);
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
    ResponseBodyDeferringAsyncHandler handler = new ResponseBodyDeferringAsyncHandler(future, BUFFER_SIZE, workersExecutor);
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
    ResponseBodyDeferringAsyncHandler handler = new ResponseBodyDeferringAsyncHandler(future, BUFFER_SIZE, workersExecutor);
    GrizzlyResponseBodyPart bodyPartBeforeError = mock(GrizzlyResponseBodyPart.class, RETURNS_DEEP_STUBS);
    GrizzlyResponseBodyPart bodyPartAfterError = mock(GrizzlyResponseBodyPart.class, RETURNS_DEEP_STUBS);

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
    ResponseBodyDeferringAsyncHandler handler = new ResponseBodyDeferringAsyncHandler(future, BUFFER_SIZE, workersExecutor);
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
    ResponseBodyDeferringAsyncHandler handler = new ResponseBodyDeferringAsyncHandler(future, BUFFER_SIZE, workersExecutor);
    GrizzlyResponseBodyPart bodyPart = mock(GrizzlyResponseBodyPart.class, RETURNS_DEEP_STUBS);
    when(bodyPart.isLast()).thenReturn(false);
    when(bodyPart.getBodyPartBytes()).thenReturn("payload".getBytes());
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
    ResponseBodyDeferringAsyncHandler handler = new ResponseBodyDeferringAsyncHandler(future, BUFFER_SIZE, workersExecutor);
    handler.onStatusReceived(mock(HttpResponseStatus.class, RETURNS_DEEP_STUBS));
    GrizzlyResponseBodyPart bodyPart = spy(new GrizzlyResponseBodyPart(mock(HttpContent.class), mock(Connection.class)));
    when(bodyPart.isLast()).thenReturn(false);
    doReturn("You will call me Snowball because my fur is pretty and white.".getBytes()).when(bodyPart).getBodyPartBytes();
    handler.onBodyPartReceived(bodyPart);
    handler.closeOut();
    assertThat(handler.onBodyPartReceived(bodyPart), is(ABORT));
  }

  @Test
  public void doesNotThrowExceptionIfContentLengthIsGreaterThanMaxInteger() throws Exception {
    CompletableFuture<HttpResponse> future = new CompletableFuture<>();
    ResponseBodyDeferringAsyncHandler handler = new ResponseBodyDeferringAsyncHandler(future, -1, workersExecutor);
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
    ResponseBodyDeferringAsyncHandler handler = new ResponseBodyDeferringAsyncHandler(future, BUFFER_SIZE, workersExecutor);
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

  private static GrizzlyResponseBodyPart mockBodyPart(boolean isLast, byte[] content) throws IOException {
    GrizzlyResponseBodyPart bodyPart = mock(GrizzlyResponseBodyPart.class, RETURNS_DEEP_STUBS);
    when(bodyPart.isLast()).thenReturn(isLast);
    when(bodyPart.getBodyByteBuffer()).thenReturn(wrap(content));
    doAnswer(invocation -> {
      OutputStream outputStream = invocation.getArgument(0);
      outputStream.write(content);
      return content.length;
    }).when(bodyPart).writeTo(any(OutputStream.class));
    return bodyPart;
  }
}
