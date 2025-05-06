/*
 * Copyright 2023 Salesforce, Inc. All rights reserved.
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.service.http.impl.service.server.sse;

import static org.mule.runtime.http.api.HttpHeaders.Names.CONNECTION;
import static org.mule.runtime.http.api.HttpHeaders.Values.CLOSE;

import static java.util.Optional.ofNullable;

import org.mule.runtime.http.api.domain.message.request.HttpRequest;
import org.mule.runtime.http.api.domain.message.response.HttpResponse;
import org.mule.runtime.http.api.server.async.HttpResponseReadyCallback;
import org.mule.runtime.http.api.sse.server.SseRequestContext;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public class SseRequestContextImpl implements SseRequestContext {

  private final HttpRequest request;
  private final HttpResponseReadyCallback responseCallback;
  private String clientId = null;
  private boolean rejectResponseSent;

  public SseRequestContextImpl(HttpRequest request, HttpResponseReadyCallback responseCallback) {
    this.request = request;
    this.responseCallback = responseCallback;
    this.rejectResponseSent = false;
  }

  @Override
  public HttpRequest getRequest() {
    return request;
  }

  @Override
  public void setClientId(String overrideId) {
    this.clientId = overrideId;
  }

  @Override
  public CompletableFuture<Void> reject(int statusCode, String reasonPhrase) {
    HttpResponse response = HttpResponse.builder()
        .statusCode(statusCode)
        .reasonPhrase(reasonPhrase)
        .addHeader(CONNECTION, CLOSE)
        .build();
    CompletableFuture<Void> future = new CompletableFuture<>();
    rejectResponseSent = true;
    responseCallback.responseReady(response, new FutureCompleterCallback(future));
    return future;
  }

  public boolean isResponded() {
    return rejectResponseSent;
  }

  public Optional<String> getClientId() {
    return ofNullable(clientId);
  }
}
