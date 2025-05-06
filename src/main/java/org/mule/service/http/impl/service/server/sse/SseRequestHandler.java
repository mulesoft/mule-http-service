/*
 * Copyright 2023 Salesforce, Inc. All rights reserved.
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.service.http.impl.service.server.sse;

import static org.mule.runtime.http.api.HttpConstants.HttpStatus.OK;
import static org.mule.runtime.http.api.HttpHeaders.Names.ACCESS_CONTROL_ALLOW_ORIGIN;
import static org.mule.runtime.http.api.HttpHeaders.Names.CACHE_CONTROL;
import static org.mule.runtime.http.api.HttpHeaders.Names.CONTENT_TYPE;
import static org.mule.runtime.http.api.HttpHeaders.Names.PRAGMA;
import static org.mule.runtime.http.api.HttpHeaders.Names.TRANSFER_ENCODING;
import static org.mule.runtime.http.api.HttpHeaders.Values.CHUNKED;
import static org.mule.runtime.http.api.HttpHeaders.Values.NO_CACHE;
import static org.mule.runtime.http.api.HttpHeaders.Values.TEXT_EVENT_STREAM;

import static java.nio.charset.StandardCharsets.UTF_8;

import org.mule.runtime.core.api.util.UUID;
import org.mule.runtime.http.api.domain.entity.EmptyHttpEntity;
import org.mule.runtime.http.api.domain.entity.InputStreamHttpEntity;
import org.mule.runtime.http.api.domain.message.response.HttpResponse;
import org.mule.runtime.http.api.domain.message.response.HttpResponseBuilder;
import org.mule.runtime.http.api.domain.request.HttpRequestContext;
import org.mule.runtime.http.api.server.RequestHandler;
import org.mule.runtime.http.api.server.async.HttpResponseReadyCallback;
import org.mule.runtime.http.api.sse.server.SseClient;
import org.mule.runtime.http.api.sse.server.SseRequestContext;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.Writer;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Server side SSE endpoint.
 */
public class SseRequestHandler implements RequestHandler {

  private static final String SSE_CACHE_CONTROL = "no-cache, no-store, max-age=0, must-revalidate";
  private static final String CORS_WILDCARD = "*";
  private final Consumer<SseRequestContext> onRequest;
  private final Consumer<SseClient> onSseClient;

  public SseRequestHandler(Consumer<SseRequestContext> onRequest,
                           Consumer<SseClient> onSseClient) {
    this.onRequest = onRequest;
    this.onSseClient = onSseClient;
  }

  @Override
  public void handleRequest(HttpRequestContext requestContext, HttpResponseReadyCallback responseCallback) {
    SseRequestContextImpl context = new SseRequestContextImpl(requestContext.getRequest(), responseCallback);
    onRequest.accept(context);
    if (context.isResponded()) {
      return;
    }

    HttpResponseBuilder responseBuilder = HttpResponse.builder()
        .statusCode(OK.getStatusCode()).reasonPhrase(OK.getReasonPhrase())
        .addHeader(CONTENT_TYPE, TEXT_EVENT_STREAM)
        .addHeader(CACHE_CONTROL, SSE_CACHE_CONTROL)
        .addHeader(PRAGMA, NO_CACHE)
        .addHeader(TRANSFER_ENCODING, CHUNKED)
        .addHeader(ACCESS_CONTROL_ALLOW_ORIGIN, CORS_WILDCARD)
        .entity(new InputStreamHttpEntity(new ByteArrayInputStream(new byte[0])));
    HttpResponse sseResponse = responseBuilder.build();

    CompletableFuture<Void> responseSentFuture = new CompletableFuture<>();
    Writer bodyWriter = responseCallback.startResponse(sseResponse, new FutureCompleterCallback(responseSentFuture), UTF_8);
    GrizzlySseClient sseClient =
        new GrizzlySseClient(bodyWriter, context.getClientId().orElseGet(this::createUUID), responseSentFuture);
    onSseClient.accept(sseClient);
  }

  private String createUUID() {
    // TODO (W-18041272): Make it cluster-aware.
    return UUID.getUUID();
  }
}
