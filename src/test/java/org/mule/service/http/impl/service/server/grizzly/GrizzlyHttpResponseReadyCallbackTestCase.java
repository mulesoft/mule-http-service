/*
 * Copyright 2023 Salesforce, Inc. All rights reserved.
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.service.http.impl.service.server.grizzly;

import static java.nio.charset.Charset.defaultCharset;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.mule.runtime.http.api.domain.message.response.HttpResponse;
import org.mule.runtime.http.api.server.RequestHandler;
import org.mule.runtime.http.api.sse.server.SseClientConfig;
import org.mule.service.http.common.server.sse.FutureCompleterCallback;
import org.mule.tck.junit4.AbstractMuleTestCase;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;

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
import org.junit.Before;
import org.junit.Test;

public class GrizzlyHttpResponseReadyCallbackTestCase extends AbstractMuleTestCase {

  private GrizzlyHttpRequestAdapter httpRequest;
  private FilterChainContext ctx;
  private RequestHandler requestHandler;
  private HttpRequestPacket requestPacket;
  private FutureCompleterCallback responseStatusCallback;
  private CompletableFuture<Void> responseFuture;

  private GrizzlyHttpResponseReadyCallback readyCallback;

  @Before
  public void setUp() {
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
  public void responseReadyStartsResponseWriting() {
    callResponseReady();
    verify(ctx).write(any(HttpContent.class), any());
  }

  @Test
  public void startResponseStartsResponseWriting() throws IOException {
    callStartResponse();
    verify(ctx).write(any(HttpContent.class), any());
  }

  @Test
  public void startSseResponseStartsResponseWriting() throws IOException {
    callStartSseResponse();
    verify(ctx).write(any(HttpContent.class), any());
  }

  @Test
  public void canNotStartResponseTwice_responseReady_responseReady() throws IOException {
    callResponseReady();
    var error = assertThrows(IllegalStateException.class, this::callResponseReady);
    assertThat(error.getMessage(), Matchers.containsString("Response was already initiated for ctx "));
  }

  @Test
  public void canNotStartResponseTwice_responseReady_startResponse() throws IOException {
    callResponseReady();
    var error = assertThrows(IllegalStateException.class, this::callStartResponse);
    assertThat(error.getMessage(), Matchers.containsString("Response was already initiated for ctx "));
  }

  @Test
  public void canNotStartResponseTwice_responseReady_startSseResponse() throws IOException {
    callResponseReady();
    var error = assertThrows(IllegalStateException.class, this::callStartSseResponse);
    assertThat(error.getMessage(), Matchers.containsString("Response was already initiated for ctx "));
  }

  @Test
  public void canNotStartResponseTwice_startResponse_responseReady() throws IOException {
    callStartResponse();
    var error = assertThrows(IllegalStateException.class, this::callResponseReady);
    assertThat(error.getMessage(), Matchers.containsString("Response was already initiated for ctx "));
  }

  @Test
  public void canNotStartResponseTwice_startResponse_startResponse() throws IOException {
    callStartResponse();
    var error = assertThrows(IllegalStateException.class, this::callStartResponse);
    assertThat(error.getMessage(), Matchers.containsString("Response was already initiated for ctx "));
  }

  @Test
  public void canNotStartResponseTwice_startResponse_startSseResponse() throws IOException {
    callStartResponse();
    var error = assertThrows(IllegalStateException.class, this::callStartSseResponse);
    assertThat(error.getMessage(), Matchers.containsString("Response was already initiated for ctx "));
  }

  @Test
  public void canNotStartResponseTwice_startSseResponse_responseReady() throws IOException {
    callStartSseResponse();
    var error = assertThrows(IllegalStateException.class, this::callResponseReady);
    assertThat(error.getMessage(), Matchers.containsString("Response was already initiated for ctx "));
  }

  @Test
  public void canNotStartResponseTwice_startSseResponse_startResponse() throws IOException {
    callStartSseResponse();
    var error = assertThrows(IllegalStateException.class, this::callStartResponse);
    assertThat(error.getMessage(), Matchers.containsString("Response was already initiated for ctx "));
  }

  @Test
  public void canNotStartResponseTwice_startSseResponse_startSseResponse() throws IOException {
    callStartSseResponse();
    var error = assertThrows(IllegalStateException.class, this::callStartSseResponse);
    assertThat(error.getMessage(), Matchers.containsString("Response was already initiated for ctx "));
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
