/*
 * Copyright 2023 Salesforce, Inc. All rights reserved.
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.service.http.impl.service.client.sse;

import static org.mule.runtime.http.api.HttpConstants.HttpStatus.OK;
import static org.mule.runtime.http.api.HttpConstants.Method.GET;
import static org.mule.runtime.http.api.HttpHeaders.Names.ACCEPT;
import static org.mule.runtime.http.api.HttpHeaders.Names.CACHE_CONTROL;
import static org.mule.runtime.http.api.HttpHeaders.Names.CONTENT_TYPE;
import static org.mule.runtime.http.api.HttpHeaders.Names.LAST_EVENT_ID;
import static org.mule.runtime.http.api.HttpHeaders.Values.NO_CACHE;
import static org.mule.runtime.http.api.HttpHeaders.Values.TEXT_EVENT_STREAM;

import org.mule.runtime.http.api.client.HttpRequestOptions;
import org.mule.runtime.http.api.domain.entity.EmptyHttpEntity;
import org.mule.runtime.http.api.domain.message.request.HttpRequest;
import org.mule.runtime.http.api.domain.message.request.HttpRequestBuilder;
import org.mule.runtime.http.api.domain.message.response.HttpResponse;
import org.mule.runtime.http.api.sse.ServerSentEvent;
import org.mule.runtime.http.api.sse.client.SseFailureContext;
import org.mule.runtime.http.api.sse.client.SseListener;
import org.mule.runtime.http.api.sse.client.SseSource;
import org.mule.runtime.http.api.sse.client.SseSourceConfig;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * Implementation of {@link SseSource} based on
 * <a href="https://html.spec.whatwg.org/multipage/server-sent-events.html">server-sent-events spec</a>.
 */
public class DefaultSseSource implements SseSource, SseListener, InternalConnectable {

  private final String uri;
  private final InternalClient httpClient;
  private final Consumer<HttpRequestBuilder> requestCustomizer;
  private final HttpRequestOptions requestOptions;
  private final RetryHelper retryHelper;

  private final AtomicInteger readyState;

  private final Map<String, SseListener> eventListenersByTopic = new ConcurrentHashMap<>();
  private SseListener fallbackListener = event -> {
    // Do nothing by default.
  };

  private String lastEventId = null;
  private final List<Consumer<SseFailureContext>> onConnectionFailureCallbacks = new ArrayList<>();
  private CompletableFuture<HttpResponse> responseFuture;

  private final boolean preserveHeaderCase;

  public DefaultSseSource(SseSourceConfig config,
                          InternalClient httpClient,
                          ScheduledExecutorService retryScheduler) {
    this.uri = config.getUrl();
    this.httpClient = httpClient;
    this.requestCustomizer = config.getRequestCustomizer();
    this.requestOptions = config.getRequestOptions();
    this.readyState = new AtomicInteger(READY_STATUS_CLOSED);
    this.retryHelper = new RetryHelper(retryScheduler, config.getRetryConfig(), this);
    this.preserveHeaderCase = config.isPreserveHeaderCase();
  }

  @Override
  public int getReadyState() {
    return readyState.get();
  }

  @Override
  public synchronized void open() {
    if (READY_STATUS_CLOSED != readyState.get()) {
      // Connection is not needed, so we just skip.
      return;
    }

    internalConnect();
  }

  @Override
  public void doOnConnectionFailure(Consumer<SseFailureContext> onConnectionFailure) {
    onConnectionFailureCallbacks.add(onConnectionFailure);
  }

  @Override
  public void register(SseListener listener) {
    fallbackListener = listener;
  }

  @Override
  public void register(String eventName, SseListener listener) {
    eventListenersByTopic.put(eventName, listener);
  }

  @Override
  public synchronized void close() {
    if (null != responseFuture) {
      responseFuture.cancel(true);
    }
    retryHelper.abortReties();
    this.onClose();
  }

  @Override
  public synchronized void onEvent(ServerSentEvent event) {
    if (READY_STATUS_CLOSED == readyState.get()) {
      throw new IllegalStateException("SSE source is already closed");
    }
    event.getId().ifPresent(id -> lastEventId = id);
    event.getRetryDelay().ifPresent(retryHelper::setDelayIfAllowed);
    eventListenersByTopic.getOrDefault(event.getName(), fallbackListener).onEvent(event);
  }

  @Override
  public synchronized void onClose() {
    if (retryHelper.shouldRetryOnStreamEnd()) {
      readyState.set(READY_STATUS_CONNECTING);
      retryHelper.scheduleReconnection();
    } else {
      eventListenersByTopic.values().forEach(SseListener::onClose);
      fallbackListener.onClose();
      readyState.set(READY_STATUS_CLOSED);
    }
  }

  @Override
  public synchronized void internalConnect() {
    readyState.set(READY_STATUS_CONNECTING);
    HttpRequest request = createInitiatorRequest();
    ProgressiveBodyDataListener dataListener = new ServerSentEventDecoder(this);
    responseFuture = httpClient.doSendAsync(request, requestOptions, dataListener)
        .whenComplete(this::handleResponseOrError);
  }

  private synchronized void handleResponseOrError(HttpResponse httpResponse, Throwable error) {
    if (isSuccessfullyConnected(httpResponse)) {
      readyState.set(READY_STATUS_OPEN);
      return;
    }

    SseFailureContext ctx = new SseFailureContextImpl(httpResponse, error, retryHelper);
    for (Consumer<SseFailureContext> callback : onConnectionFailureCallbacks) {
      callback.accept(ctx);
    }

    // Some error callback could stop the retry mechanism
    if (!retryHelper.isRetryEnabled()) {
      onClose();
      return;
    }

    readyState.set(READY_STATUS_CONNECTING);
    retryHelper.scheduleReconnection();
  }

  private boolean isSuccessfullyConnected(HttpResponse httpResponse) {
    if (null == httpResponse) {
      return false;
    }

    if (OK.getStatusCode() != httpResponse.getStatusCode()) {
      return false;
    }

    return TEXT_EVENT_STREAM.equalsIgnoreCase(httpResponse.getHeaderValue(CONTENT_TYPE));
  }

  private HttpRequest createInitiatorRequest() {
    var builder = HttpRequest.builder(preserveHeaderCase).method(GET)
        .uri(uri)
        .addHeader(ACCEPT, TEXT_EVENT_STREAM)
        .addHeader(CACHE_CONTROL, NO_CACHE)
        .entity(new EmptyHttpEntity());
    if (null != lastEventId) {
      builder.addHeader(LAST_EVENT_ID, lastEventId);
    }
    requestCustomizer.accept(builder);
    return builder.build();
  }
}
