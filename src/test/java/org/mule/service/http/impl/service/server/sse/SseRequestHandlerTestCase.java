/*
 * Copyright 2023 Salesforce, Inc. All rights reserved.
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.service.http.impl.service.server.sse;

import static org.mule.service.http.impl.AllureConstants.HttpFeature.HTTP_SERVICE;
import static org.mule.service.http.impl.AllureConstants.HttpFeature.HttpStory.SSE;
import static org.mule.service.http.impl.AllureConstants.HttpFeature.HttpStory.SSE_ENDPOINT;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.mule.runtime.http.api.domain.message.request.HttpRequest;
import org.mule.runtime.http.api.domain.message.response.HttpResponse;
import org.mule.runtime.http.api.domain.request.HttpRequestContext;
import org.mule.runtime.http.api.server.async.HttpResponseReadyCallback;
import org.mule.runtime.http.api.sse.server.SseClient;
import org.mule.runtime.http.api.sse.server.SseRequestContext;
import org.mule.tck.junit4.AbstractMuleTestCase;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import io.qameta.allure.Feature;
import io.qameta.allure.Story;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

@Feature(HTTP_SERVICE)
@Story(SSE)
@Story(SSE_ENDPOINT)
public class SseRequestHandlerTestCase extends AbstractMuleTestCase {

  private HttpResponseReadyCallback responseReadyCallback;
  private HttpRequestContext requestContext;
  private HttpRequest httpRequest;

  @Before
  public void setUp() throws Exception {
    responseReadyCallback = mock(HttpResponseReadyCallback.class);
    requestContext = mock(HttpRequestContext.class);
    httpRequest = mock(HttpRequest.class);
    when(requestContext.getRequest()).thenReturn(httpRequest);
  }

  @Test
  public void whenRequestCallbackDoesNotReject_theOnSseClientCallbackIsCalled() {
    Consumer<SseRequestContext> onRequest = requestCtx -> {
      // Nothing to do...
    };
    Consumer<SseClient> onSseClient = mock(Consumer.class);
    SseRequestHandler sseRequestHandler =
        new SseRequestHandler(onRequest, onSseClient);
    sseRequestHandler.handleRequest(requestContext, responseReadyCallback);

    verify(onSseClient).accept(any());
  }

  @Test
  public void whenRequestCallbackDoesNotReject_theResponseBodyDeferringMechanismIsUsed() {
    Consumer<SseRequestContext> onRequest = requestCtx -> {
      // Nothing to do...
    };
    Consumer<SseClient> onSseClient = mock(Consumer.class);
    SseRequestHandler sseRequestHandler =
        new SseRequestHandler(onRequest, onSseClient);
    sseRequestHandler.handleRequest(requestContext, responseReadyCallback);

    verify(responseReadyCallback).startResponse(any(), any(), any());
    verify(responseReadyCallback, never()).responseReady(any(), any());
  }

  @Test
  public void whenRequestCallbackRejects_theOnSseClientCallbackIsNotCalled() {
    Consumer<SseRequestContext> onRequest = requestCtx -> requestCtx.reject(500, "Expected error");
    Consumer<SseClient> onSseClient = mock(Consumer.class);
    SseRequestHandler sseRequestHandler =
        new SseRequestHandler(onRequest, onSseClient);
    sseRequestHandler.handleRequest(requestContext, responseReadyCallback);

    verify(responseReadyCallback, never()).startResponse(any(), any(), any());
    verify(responseReadyCallback).responseReady(any(), any());
  }

  @Test
  public void whenRequestCallbackRejects_theResponseDeferringMechanismIsNotUsed() {
    Consumer<SseRequestContext> onRequest = requestCtx -> requestCtx.reject(500, "Expected error");
    Consumer<SseClient> onSseClient = mock(Consumer.class);
    SseRequestHandler sseRequestHandler =
        new SseRequestHandler(onRequest, onSseClient);
    sseRequestHandler.handleRequest(requestContext, responseReadyCallback);

    verify(onSseClient, never()).accept(any());
  }

  @Test
  public void whenRequestCallbackSetsTheId_theOnSseClientCallbackSeesTheId() {
    // This is also an example of how to override the client id based on a header...
    String overriddenId = "OverriddenId";
    when(httpRequest.getHeaderValue("X-Override-Id")).thenReturn(overriddenId);

    Consumer<SseRequestContext> onRequest = requestCtx -> {
      String headerValue = requestCtx.getRequest().getHeaderValue("X-Override-Id");
      if (null != headerValue) {
        requestCtx.setClientId(headerValue);
      }
    };

    AtomicReference<String> seenId = new AtomicReference<>();
    Consumer<SseClient> onSseClient = sseClient -> seenId.set(sseClient.getClientId());
    SseRequestHandler sseRequestHandler =
        new SseRequestHandler(onRequest, onSseClient);

    sseRequestHandler.handleRequest(requestContext, responseReadyCallback);

    assertThat(seenId.get(), is(overriddenId));
  }

  @Test
  public void checkDefaultResponse() {
    Consumer<SseRequestContext> onRequest = mock(Consumer.class);
    Consumer<SseClient> onSseClient = mock(Consumer.class);
    SseRequestHandler sseRequestHandler =
        new SseRequestHandler(onRequest, onSseClient);

    sseRequestHandler.handleRequest(requestContext, responseReadyCallback);

    ArgumentCaptor<HttpResponse> responseCaptor = ArgumentCaptor.forClass(HttpResponse.class);
    verify(responseReadyCallback).startResponse(responseCaptor.capture(), any(), any());

    HttpResponse response = responseCaptor.getValue();
    assertThat(response.getStatusCode(), is(200));
    assertThat(response.getHeaderNames(),
               containsInAnyOrder("content-type", "cache-control", "pragma", "transfer-encoding", "access-control-allow-origin"));
    assertThat(response.getHeaderValue("Content-Type"), is("text/event-stream"));
  }

}
