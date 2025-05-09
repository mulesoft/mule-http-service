/*
 * Copyright 2023 Salesforce, Inc. All rights reserved.
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.service.http.impl.service.server;

import org.mule.runtime.api.tls.TlsContextFactory;
import org.mule.runtime.http.api.HttpConstants;
import org.mule.runtime.http.api.server.HttpServer;
import org.mule.runtime.http.api.server.RequestHandler;
import org.mule.runtime.http.api.server.RequestHandlerManager;
import org.mule.runtime.http.api.server.ServerAddress;
import org.mule.runtime.http.api.server.ws.WebSocketHandler;
import org.mule.runtime.http.api.server.ws.WebSocketHandlerManager;
import org.mule.runtime.http.api.sse.server.SseClient;
import org.mule.runtime.http.api.sse.server.SseEndpointManager;
import org.mule.runtime.http.api.sse.server.SseRequestContext;

import java.io.IOException;
import java.util.Collection;
import java.util.function.Consumer;

/**
 * Base class for applying the delegate design pattern around an {@link HttpServer}
 *
 * @since 1.3.0
 */
public class HttpServerDelegate implements HttpServer {

  protected final HttpServer delegate;

  public HttpServerDelegate(HttpServer delegate) {
    this.delegate = delegate;
  }

  public HttpServer getDelegate() {
    return delegate;
  }

  @Override
  public HttpServer start() throws IOException {
    return delegate.start();
  }

  @Override
  public HttpServer stop() {
    return delegate.stop();
  }

  @Override
  public void dispose() {
    delegate.dispose();
  }

  @Override
  public ServerAddress getServerAddress() {
    return delegate.getServerAddress();
  }

  @Override
  public HttpConstants.Protocol getProtocol() {
    return delegate.getProtocol();
  }

  @Override
  public boolean isStopping() {
    return delegate.isStopping();
  }

  @Override
  public boolean isStopped() {
    return delegate.isStopped();
  }

  @Override
  public RequestHandlerManager addRequestHandler(Collection<String> methods, String path, RequestHandler requestHandler) {
    return delegate.addRequestHandler(methods, path, requestHandler);
  }

  @Override
  public RequestHandlerManager addRequestHandler(String path, RequestHandler requestHandler) {
    return delegate.addRequestHandler(path, requestHandler);
  }

  @Override
  public WebSocketHandlerManager addWebSocketHandler(WebSocketHandler handler) {
    return delegate.addWebSocketHandler(handler);
  }

  @Override
  public void enableTls(TlsContextFactory tlsContextFactory) {
    delegate.enableTls(tlsContextFactory);
  }

  @Override
  public void disableTls() {
    delegate.disableTls();
  }

  public SseEndpointManager sse(String ssePath, Consumer<SseRequestContext> onRequest, Consumer<SseClient> onClient) {
    return delegate.sse(ssePath, onRequest, onClient);
  }

  public SseEndpointManager sse(String ssePath, Consumer<SseClient> sseClientHandler) {
    return delegate.sse(ssePath, sseClientHandler);
  }
}
