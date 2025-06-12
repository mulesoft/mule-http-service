/*
 * Copyright 2023 Salesforce, Inc. All rights reserved.
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.service.http.impl.service.server.grizzly;

import static org.mule.runtime.http.api.HttpConstants.Method.HEAD;

import org.mule.runtime.http.api.domain.entity.EmptyHttpEntity;
import org.mule.runtime.http.api.domain.message.response.HttpResponse;
import org.mule.runtime.http.api.domain.message.response.HttpResponseBuilder;
import org.mule.runtime.http.api.server.RequestHandler;
import org.mule.runtime.http.api.server.async.HttpResponseReadyCallback;
import org.mule.runtime.http.api.server.async.ResponseStatusCallback;
import org.mule.runtime.http.api.sse.server.SseClient;
import org.mule.runtime.http.api.sse.server.SseClientConfig;
import org.mule.service.http.common.server.sse.SseResponseStarter;

import java.io.Writer;
import java.nio.charset.Charset;
import java.util.concurrent.atomic.AtomicBoolean;

import org.glassfish.grizzly.filterchain.FilterChainContext;
import org.glassfish.grizzly.http.HttpRequestPacket;

/**
 * Implementation of {@link HttpResponseReadyCallback} based on Grizzly. It only allows one invocation of either
 * {@link #responseReady(HttpResponse, ResponseStatusCallback)} or
 * {@link #startResponse(HttpResponse, ResponseStatusCallback, Charset)}.
 */
public class GrizzlyHttpResponseReadyCallback implements HttpResponseReadyCallback {

  private final GrizzlyHttpRequestAdapter httpRequest;
  private final FilterChainContext ctx;
  private final RequestHandler requestHandler;
  private final HttpRequestPacket request;

  private final AtomicBoolean httpHeaderSent = new AtomicBoolean(false);

  public GrizzlyHttpResponseReadyCallback(GrizzlyHttpRequestAdapter httpRequest,
                                          FilterChainContext ctx,
                                          RequestHandler requestHandler,
                                          HttpRequestPacket request) {
    this.httpRequest = httpRequest;
    this.ctx = ctx;
    this.requestHandler = requestHandler;
    this.request = request;
  }

  @Override
  public void responseReady(HttpResponse response, ResponseStatusCallback responseStatusCallback) {
    if (httpHeaderSent.compareAndSet(false, true)) {
      try {
        if (httpRequest.getMethod().equals(HEAD.name())) {
          if (response.getEntity().isStreaming()) {
            response.getEntity().getContent().close();
          }
          response = new HttpResponseBuilder(response).entity(new EmptyHttpEntity()).build();
        }

        // We need to notify the request adapter when the response has been sent, this way it can be protected
        // against further reading attempts
        final ResponseStatusCallback requestAdapterNotifyingResponseStatusCallback =
            new RequestAdapterNotifyingResponseStatusCallback(httpRequest, responseStatusCallback);

        if (response.getEntity().isStreaming()) {
          new ResponseStreamingCompletionHandler(ctx, requestHandler.getContextClassLoader(), request, response,
                                                 requestAdapterNotifyingResponseStatusCallback).start();
        } else {
          new ResponseCompletionHandler(ctx, requestHandler.getContextClassLoader(), request, response,
                                        requestAdapterNotifyingResponseStatusCallback).start();
        }
      } catch (Exception e) {
        httpHeaderSent.set(false);
        responseStatusCallback.responseSendFailure(e);
      }
    } else {
      throw new IllegalStateException("Response was already initiated for ctx " + ctx.toString());
    }
  }

  @Override
  public Writer startResponse(HttpResponse response, ResponseStatusCallback responseStatusCallback, Charset encoding) {
    if (httpHeaderSent.compareAndSet(false, true)) {
      ResponseDelayedCompletionHandler responseCompletionHandler =
          new ResponseDelayedCompletionHandler(ctx, requestHandler.getContextClassLoader(), request, response,
                                               responseStatusCallback);
      return responseCompletionHandler.buildWriter(encoding);
    } else {
      throw new IllegalStateException("Response was already initiated for ctx " + ctx.toString());
    }
  }

  @Override
  public SseClient startSseResponse(SseClientConfig config) {
    // Note: we don't check for httpHeaderSent here because the implementation ends up calling startResponse and the check is done
    // there
    return new SseResponseStarter().startResponse(config, this);
  }
}
