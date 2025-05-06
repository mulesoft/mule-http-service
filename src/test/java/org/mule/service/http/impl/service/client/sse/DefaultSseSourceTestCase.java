/*
 * Copyright 2023 Salesforce, Inc. All rights reserved.
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.service.http.impl.service.client.sse;

import static org.mule.functional.junit4.matchers.ThrowableMessageMatcher.hasMessage;
import static org.mule.runtime.http.api.HttpHeaders.Names.ACCEPT;
import static org.mule.runtime.http.api.HttpHeaders.Names.CACHE_CONTROL;
import static org.mule.runtime.http.api.HttpHeaders.Names.CONTENT_TYPE;
import static org.mule.runtime.http.api.HttpHeaders.Values.NO_CACHE;
import static org.mule.runtime.http.api.HttpHeaders.Values.TEXT_EVENT_STREAM;
import static org.mule.runtime.http.api.sse.client.SseRetryConfig.DEFAULT_RETRY_DELAY_MILLIS;
import static org.mule.runtime.http.api.sse.client.SseSource.READY_STATUS_CLOSED;
import static org.mule.runtime.http.api.sse.client.SseSource.READY_STATUS_CONNECTING;
import static org.mule.runtime.http.api.sse.client.SseSource.READY_STATUS_OPEN;
import static org.mule.service.http.impl.AllureConstants.HttpFeature.HTTP_SERVICE;
import static org.mule.service.http.impl.AllureConstants.HttpFeature.HttpStory.SSE;
import static org.mule.service.http.impl.AllureConstants.HttpFeature.HttpStory.SSE_RETRY;
import static org.mule.service.http.impl.AllureConstants.HttpFeature.HttpStory.SSE_SOURCE;
import static org.mule.service.http.impl.util.sse.ServerSentEventTypeSafeMatcher.aServerSentEvent;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.concurrent.CompletableFuture.completedFuture;
import static java.util.concurrent.CompletableFuture.failedFuture;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.equalToIgnoringCase;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.mule.runtime.api.scheduler.Scheduler;
import org.mule.runtime.http.api.client.HttpRequestOptions;
import org.mule.runtime.http.api.domain.message.request.HttpRequest;
import org.mule.runtime.http.api.domain.message.request.HttpRequestBuilder;
import org.mule.runtime.http.api.domain.message.response.HttpResponse;
import org.mule.runtime.http.api.sse.client.SseListener;
import org.mule.runtime.http.api.sse.client.SseRetryConfig;
import org.mule.runtime.http.api.sse.client.SseSource;
import org.mule.runtime.http.api.sse.client.SseSourceConfig;
import org.mule.service.http.impl.service.message.sse.ServerSentEventImpl;
import org.mule.service.http.impl.util.sse.SSEEventsAggregator;
import org.mule.tck.junit4.AbstractMuleTestCase;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Collection;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Story;
import org.junit.Before;
import org.junit.Test;
import org.junit.function.ThrowingRunnable;
import org.mockito.ArgumentCaptor;
import org.mockito.stubbing.Answer;

@Feature(HTTP_SERVICE)
@Story(SSE)
@Story(SSE_SOURCE)
public class DefaultSseSourceTestCase extends AbstractMuleTestCase {

  private static final SseRetryConfig DONT_RETRY_ON_EOS = new SseRetryConfig(true, 2000L, false);

  private static final String TEST_URL = "http://localhost:8080/test";

  private InternalClient mockClient;
  private Scheduler mockScheduler;
  private HttpRequestOptions mockOptions;
  private Consumer<HttpRequestBuilder> mockCustomizer;

  private SseSourceConfig configWithRetry;
  private SseSourceConfig configDontRetry;

  @Before
  public void setUp() {
    mockScheduler = mock(Scheduler.class);
    mockClient = mock(InternalClient.class);
    mockOptions = mock(HttpRequestOptions.class);
    mockCustomizer = mock(Consumer.class);

    configWithRetry = SseSourceConfig.builder(TEST_URL)
        .withRequestOptions(mockOptions)
        .withRequestCustomizer(mockCustomizer)
        .build();

    configDontRetry = SseSourceConfig.builder(TEST_URL)
        .withRetryConfig(DONT_RETRY_ON_EOS)
        .withRequestOptions(mockOptions)
        .withRequestCustomizer(mockCustomizer)
        .build();
  }

  @Test
  public void newEventSourceIsClosed() {
    try (SseSource eventSource = new DefaultSseSource(configDontRetry, mockClient, mockScheduler)) {
      assertThat(eventSource.getReadyState(), is(READY_STATUS_CLOSED));
    }
  }

  @Test
  public void openEventSourceSendsRequestWithExpectedHeaders() {
    try (SseSource eventSource = new DefaultSseSource(configDontRetry, mockClient, mockScheduler)) {
      CompletableFuture<HttpResponse> response = getOkResponse();
      when(mockClient.doSendAsync(any(), any(), any())).thenReturn(response);
      eventSource.open();

      ArgumentCaptor<HttpRequest> requestCaptor = forClass(HttpRequest.class);
      verify(mockClient).doSendAsync(requestCaptor.capture(), any(), any());
      HttpRequest initiatorRequest = requestCaptor.getValue();

      assertThat(initiatorRequest.getMethod(), is("GET"));
      assertThat(initiatorRequest.getHeaderNames(),
                 containsInAnyOrder(equalToIgnoringCase(ACCEPT), equalToIgnoringCase(CACHE_CONTROL)));
      assertThat(initiatorRequest.getHeaderValue(ACCEPT), is(TEXT_EVENT_STREAM));
      assertThat(initiatorRequest.getHeaderValue(CACHE_CONTROL), is(NO_CACHE));

      assertThat("Event source should be open", eventSource.getReadyState(), is(READY_STATUS_OPEN));
    }
  }

  @Test
  public void customRequestOptionsAreUsed() {
    try (SseSource eventSource = new DefaultSseSource(configDontRetry, mockClient, mockScheduler)) {
      CompletableFuture<HttpResponse> response = getOkResponse();
      when(mockClient.doSendAsync(any(), any(), any())).thenReturn(response);
      eventSource.open();

      ArgumentCaptor<HttpRequestOptions> optionsCaptor = forClass(HttpRequestOptions.class);
      verify(mockClient).doSendAsync(any(), optionsCaptor.capture(), any());

      assertThat(optionsCaptor.getValue(), is(sameInstance(mockOptions)));
    }
  }

  @Test
  public void initiatorRequestCustomizerIsCalledAndTheBuilderAlreadyContainsBasicHeaders() {
    try (SseSource eventSource = new DefaultSseSource(configDontRetry, mockClient, mockScheduler)) {
      CompletableFuture<HttpResponse> response = getOkResponse();
      when(mockClient.doSendAsync(any(), any(), any())).thenReturn(response);
      eventSource.open();

      ArgumentCaptor<HttpRequestBuilder> builderCaptor = forClass(HttpRequestBuilder.class);
      verify(mockCustomizer).accept(builderCaptor.capture());

      HttpRequest initiatorRequest = builderCaptor.getValue().build();

      assertThat(initiatorRequest.getHeaderNames(),
                 containsInAnyOrder(equalToIgnoringCase(ACCEPT), equalToIgnoringCase(CACHE_CONTROL)));
      assertThat(initiatorRequest.getHeaderValue(ACCEPT), is(TEXT_EVENT_STREAM));
      assertThat(initiatorRequest.getHeaderValue(CACHE_CONTROL), is(NO_CACHE));
    }
  }

  @Test
  @Story(SSE_RETRY)
  public void whenAnExceptionHappensOnConnect_sourceSchedulesReconnection() {
    try (SseSource eventSource = new DefaultSseSource(configDontRetry, mockClient, mockScheduler)) {
      CompletableFuture<HttpResponse> failure = failedFuture(new IOException("Connection reset (?)"));
      when(mockClient.doSendAsync(any(), any(), any())).thenReturn(failure);

      eventSource.open();

      assertThat(eventSource.getReadyState(), is(READY_STATUS_CONNECTING));
      verify(mockScheduler).schedule(any(Runnable.class), eq(DEFAULT_RETRY_DELAY_MILLIS), eq(MILLISECONDS));
    }
  }

  @Test
  @Story(SSE_RETRY)
  public void whenAnHttpErrorResponse_sourceSchedulesReconnection() {
    try (SseSource eventSource = new DefaultSseSource(configDontRetry, mockClient, mockScheduler)) {
      CompletableFuture<HttpResponse> res = getErrorResponse(503);
      when(mockClient.doSendAsync(any(), any(), any())).thenReturn(res);

      eventSource.open();

      assertThat(eventSource.getReadyState(), is(READY_STATUS_CONNECTING));
      verify(mockScheduler).schedule(any(Runnable.class), eq(DEFAULT_RETRY_DELAY_MILLIS), eq(MILLISECONDS));
    }
  }

  @Test
  public void whenASourceIsTriedToBeOpenedTwice_itOnlySendsOneRequest() {
    try (SseSource eventSource = new DefaultSseSource(configDontRetry, mockClient, mockScheduler)) {
      CompletableFuture<HttpResponse> responseFuture = getOkResponse();
      when(mockClient.doSendAsync(any(), any(), any())).thenReturn(responseFuture);
      eventSource.open();
      eventSource.open();

      verify(mockClient, times(1)).doSendAsync(any(), any(), any());
    }
  }

  @Test
  public void routesEventsToRegisteredListeners() throws Throwable {
    try (SseSource eventSource = new DefaultSseSource(configDontRetry, mockClient, mockScheduler)) {

      var stream = mockEventStream("""
          event: name1
          data: message1

          event: name2
          data: message2

          event: name2
          data: message3

          event: name1
          data: message4

          event: other
          data: message5

          event: yetAnother
          data: message6

          """);

      SSEEventsAggregator aggregatorForName1 = new SSEEventsAggregator();
      SSEEventsAggregator aggregatorForName2 = new SSEEventsAggregator();
      SSEEventsAggregator aggregatorForFallback = new SSEEventsAggregator();

      eventSource.register("name1", aggregatorForName1);
      eventSource.register("name2", aggregatorForName2);
      eventSource.register(aggregatorForFallback);

      eventSource.open();
      stream.run();

      assertThat(aggregatorForName1.getList(), contains(
                                                        aServerSentEvent("name1", "message1"),
                                                        aServerSentEvent("name1", "message4")));

      assertThat(aggregatorForName2.getList(), contains(
                                                        aServerSentEvent("name2", "message2"),
                                                        aServerSentEvent("name2", "message3")));

      assertThat(aggregatorForFallback.getList(), contains(
                                                           aServerSentEvent("other", "message5"),
                                                           aServerSentEvent("yetAnother", "message6")));
    }
  }

  @Test
  @Issue("W-18085890")
  public void preservesHeaderCaseOnInitiatorRequest() {
    testPreserveHeaderCase(true);
  }

  @Test
  @Issue("W-18085890")
  public void doesNotPreservesHeaderCaseOnInitiatorRequest() {
    testPreserveHeaderCase(false);
  }

  private void testPreserveHeaderCase(boolean preserveHeaderCase) {
    SseSourceConfig config = SseSourceConfig.builder(TEST_URL)
        .withPreserveHeadersCase(preserveHeaderCase)
        .build();

    try (SseSource eventSource = new DefaultSseSource(config, mockClient, mockScheduler)) {
      CompletableFuture<HttpResponse> response = getOkResponse();
      when(mockClient.doSendAsync(any(), any(), any())).thenReturn(response);
      eventSource.open();

      ArgumentCaptor<HttpRequest> requestCaptor = forClass(HttpRequest.class);
      verify(mockClient).doSendAsync(requestCaptor.capture(), any(), any());
      Collection<String> headerNames = requestCaptor.getValue().getHeaderNames();

      if (preserveHeaderCase) {
        assertThat(headerNames, containsInAnyOrder(equalTo(ACCEPT), equalTo(CACHE_CONTROL)));
      } else {
        assertThat(headerNames, containsInAnyOrder(equalTo(ACCEPT.toLowerCase()), equalTo(CACHE_CONTROL.toLowerCase())));
      }
    }
  }

  @Test
  @Story(SSE_RETRY)
  public void stopRetriesOnCertainError() {
    try (SseSource eventSource = new DefaultSseSource(configDontRetry, mockClient, mockScheduler)) {
      CompletableFuture<HttpResponse> failure = failedFuture(new IOException("Certain error"));
      when(mockClient.doSendAsync(any(), any(), any())).thenReturn(failure);

      eventSource.doOnConnectionFailure(ctx -> {
        if (null != ctx.error() && "Certain error".equals(ctx.error().getMessage())) {
          ctx.stopRetrying();
        }
      });

      eventSource.open();

      assertThat(eventSource.getReadyState(), is(READY_STATUS_CLOSED));
      verify(mockScheduler, never()).schedule(any(Runnable.class), eq(DEFAULT_RETRY_DELAY_MILLIS), eq(MILLISECONDS));
    }
  }

  @Test
  @Story(SSE_RETRY)
  public void stopRetriesOnResponse500() {
    try (SseSource eventSource = new DefaultSseSource(configDontRetry, mockClient, mockScheduler)) {
      CompletableFuture<HttpResponse> response500 = getErrorResponse(500);
      when(mockClient.doSendAsync(any(), any(), any())).thenReturn(response500);

      eventSource.doOnConnectionFailure(ctx -> {
        if (null != ctx.response() && ctx.response().getStatusCode() == 500) {
          ctx.stopRetrying();
        }
      });

      eventSource.open();

      assertThat(eventSource.getReadyState(), is(READY_STATUS_CLOSED));
      verify(mockScheduler, never()).schedule(any(Runnable.class), eq(DEFAULT_RETRY_DELAY_MILLIS), eq(MILLISECONDS));
    }
  }

  @Test
  @Story(SSE_RETRY)
  public void shouldRetryOnStreamEnd() throws Throwable {
    try (SseSource eventSource = new DefaultSseSource(configWithRetry, mockClient, mockScheduler)) {
      var stream = mockEventStream("");
      eventSource.open();
      stream.run();

      assertThat(eventSource.getReadyState(), is(READY_STATUS_CONNECTING));
      verify(mockScheduler).schedule(any(Runnable.class), eq(DEFAULT_RETRY_DELAY_MILLIS), eq(MILLISECONDS));
    }
  }

  @Test
  public void serverOverridesRetryTimeout() throws Throwable {
    // Ensure that new delay is different from default
    Long newDelay = DEFAULT_RETRY_DELAY_MILLIS + 100L;

    try (SseSource eventSource = new DefaultSseSource(configWithRetry, mockClient, mockScheduler)) {
      var stream = mockEventStream("""
          event: name1
          data: message1
          retry: %d

          """.formatted(newDelay));
      eventSource.open();
      stream.run();

      verify(mockScheduler).schedule(any(Runnable.class), eq(newDelay), eq(MILLISECONDS));
    }
  }

  @Test
  public void givenAClosedSource_itFailsWhenReceivesAnEvent() {
    SseListener mockListener = mock(SseListener.class);
    SseSource eventSource = new DefaultSseSource(configWithRetry, mockClient, mockScheduler);
    eventSource.register(mockListener);

    var stream = mockEventStream("""
        event: name
        data: message

        """);
    eventSource.open();
    eventSource.close();

    var exception = assertThrows(IllegalStateException.class, stream);
    assertThat(exception, hasMessage("SSE source is already closed"));

    verify(mockListener, never()).onEvent(any(ServerSentEventImpl.class));
  }

  @Test
  public void sourceCanBeReopened() throws Throwable {
    SseListener mockListener = mock(SseListener.class);
    SseSource eventSource = new DefaultSseSource(configWithRetry, mockClient, mockScheduler);
    eventSource.register(mockListener);

    var stream = mockEventStream("""
        event: name
        data: message

        """);

    eventSource.open();
    eventSource.close();
    eventSource.open();

    stream.run();

    verify(mockListener).onEvent(any(ServerSentEventImpl.class));
  }

  private ThrowingRunnable mockEventStream(String eventStreamPayload) {
    CompletableFuture<ProgressiveBodyDataListener> sinkReference = new CompletableFuture<>();

    when(mockClient.doSendAsync(any(), any(), any())).thenAnswer((Answer<CompletableFuture<HttpResponse>>) invocation -> {
      sinkReference.complete(invocation.getArgument(2));
      return getOkResponse();
    });

    return () -> {
      ProgressiveBodyDataListener dataSink = sinkReference.get();
      int length = eventStreamPayload.length();
      ByteArrayInputStream inputStream = new ByteArrayInputStream(eventStreamPayload.getBytes(UTF_8));
      dataSink.onStreamCreated(inputStream);
      dataSink.onDataAvailable(length);
      dataSink.onEndOfStream();
    };
  }

  private static CompletableFuture<HttpResponse> getOkResponse() {
    HttpResponse response = mock(HttpResponse.class);
    when(response.getStatusCode()).thenReturn(200);
    when(response.getHeaderValue(CONTENT_TYPE)).thenReturn(TEXT_EVENT_STREAM);
    return completedFuture(response);
  }

  private static CompletableFuture<HttpResponse> getErrorResponse(int statusCode) {
    HttpResponse response = mock(HttpResponse.class);
    when(response.getStatusCode()).thenReturn(statusCode);
    return completedFuture(response);
  }
}
