/*
 * Copyright 2023 Salesforce, Inc. All rights reserved.
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.service.http.impl.service.server.grizzly;

import static java.nio.charset.Charset.defaultCharset;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.fail;
import static org.junit.internal.matchers.ThrowableCauseMatcher.hasCause;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.mule.runtime.api.exception.MuleRuntimeException;
import org.mule.runtime.http.api.domain.entity.HttpEntity;
import org.mule.runtime.http.api.domain.message.response.HttpResponse;
import org.mule.runtime.http.api.server.RequestHandler;
import org.mule.runtime.http.api.sse.server.SseClientConfig;
import org.mule.service.http.common.server.sse.FutureCompleterCallback;
import org.mule.tck.junit4.AbstractMuleTestCase;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import org.glassfish.grizzly.Buffer;
import org.glassfish.grizzly.Connection;
import org.glassfish.grizzly.Transport;
import org.glassfish.grizzly.filterchain.FilterChainContext;
import org.glassfish.grizzly.http.HttpContent;
import org.glassfish.grizzly.http.HttpRequestPacket;
import org.glassfish.grizzly.http.Method;
import org.glassfish.grizzly.http.Protocol;
import org.glassfish.grizzly.memory.MemoryManager;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class GrizzlyHttpResponseReadyCallbackTestCase extends AbstractMuleTestCase {

  private GrizzlyHttpRequestAdapter httpRequest;
  private FilterChainContext ctx;
  private RequestHandler requestHandler;
  private HttpRequestPacket requestPacket;
  private FutureCompleterCallback responseStatusCallback;
  private CompletableFuture<Void> responseFuture;

  private GrizzlyHttpResponseReadyCallback readyCallback;

  @BeforeEach
  void setUp() {
    httpRequest = mock(GrizzlyHttpRequestAdapter.class);
    when(httpRequest.getMethod()).thenReturn("GET");

    Buffer buffer = mock(Buffer.class);
    when(buffer.array()).thenReturn(new byte[256]);
    when(buffer.arrayOffset()).thenReturn(0);

    MemoryManager memoryManager = mock(MemoryManager.class);
    when(memoryManager.allocate(anyInt())).thenReturn(buffer);

    Transport transport = mock(Transport.class);
    when(transport.getMemoryManager()).thenReturn(memoryManager);

    Connection connection = mock(Connection.class);
    when(connection.getTransport()).thenReturn(transport);

    ctx = mock(FilterChainContext.class);
    when(ctx.getConnection()).thenReturn(connection);

    requestHandler = mock(RequestHandler.class);

    requestPacket = mock(HttpRequestPacket.class);
    when(requestPacket.getProtocol()).thenReturn(Protocol.HTTP_1_1);
    when(requestPacket.getMethod()).thenReturn(Method.GET);

    readyCallback = new GrizzlyHttpResponseReadyCallback(httpRequest, ctx, requestHandler, requestPacket);

    responseFuture = new CompletableFuture<>();
    responseStatusCallback = new FutureCompleterCallback(responseFuture);
  }

  @Test
  void responseReadyStartsResponseWriting() {
    callResponseReady();
    verify(ctx).write(any(HttpContent.class), any());
  }

  @Test
  void startResponseStartsResponseWriting() throws IOException {
    callStartResponse();
    verify(ctx).write(any(HttpContent.class), any());
  }

  @Test
  void startSseResponseStartsResponseWriting() throws IOException {
    callStartSseResponse();
    verify(ctx).write(any(HttpContent.class), any());
  }

  @Test
  void whenFirstResponseReadyFails_thenASecondResponseReadyIsAllowed() throws IOException {
    var buggyEntityError = new IOException("Expected failure");
    var buggyHttpEntity = mock(HttpEntity.class);
    when(buggyHttpEntity.getBytes()).thenThrow(buggyEntityError);

    var response = HttpResponse.builder()
        .statusCode(200).reasonPhrase("OK")
        .entity(buggyHttpEntity)
        .build();
    readyCallback.responseReady(response, responseStatusCallback);

    var firstError = assertThrows(ExecutionException.class, responseFuture::get);
    assertThat(firstError, hasCause(instanceOf(MuleRuntimeException.class)));
    var runtimeException = firstError.getCause();
    assertThat(runtimeException, hasCause(sameInstance(buggyEntityError)));

    // Not yet...
    verify(ctx, never()).write(any(HttpContent.class), any());

    callResponseReady();
    verify(ctx).write(any(HttpContent.class), any());
  }

  @ParameterizedTest(name = "[{index}] {displayName} {arguments}")
  @CsvSource(textBlock = """
      responseReady,responseReady
      responseReady,startResponse
      responseReady,startSseResponse

      startResponse,responseReady
      startResponse,startResponse
      startResponse,startSseResponse

      startSseResponse,responseReady
      startSseResponse,startResponse
      startSseResponse,startSseResponse
      """)
  void canNotStartResponseTwice(String first, String second) throws IOException {
    callStartMethod(first);
    var error = assertThrows(IllegalStateException.class, () -> callStartMethod(second));
    assertThat(error.getMessage(), Matchers.containsString("Response was already initiated for ctx "));
  }

  private void callStartMethod(String method) throws IOException {
    if ("responseReady".equals(method)) {
      callResponseReady();
      return;
    }

    if ("startResponse".equals(method)) {
      callStartResponse();
      return;
    }

    if ("startSseResponse".equals(method)) {
      callStartSseResponse();
      return;
    }

    fail("Unknown method: " + method);
  }

  private void callStartResponse() throws IOException {
    HttpResponse response = HttpResponse.builder().statusCode(200).reasonPhrase("OK").build();
    var writer = readyCallback.startResponse(response, responseStatusCallback, defaultCharset());
    writer.flush();
  }

  private void callResponseReady() {
    HttpResponse response = HttpResponse.builder().statusCode(200).reasonPhrase("OK").build();
    readyCallback.responseReady(response, responseStatusCallback);
  }

  private void callStartSseResponse() throws IOException {
    SseClientConfig sseClientConfig = SseClientConfig.builder().build();
    var sseClient = readyCallback.startSseResponse(sseClientConfig);
    sseClient.sendEvent("data");
  }
}
