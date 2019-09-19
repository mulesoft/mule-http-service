/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.service.http.impl.service.server.grizzly;

import static java.lang.String.format;
import static org.mule.runtime.http.api.server.MethodRequestMatcher.acceptAll;
import org.mule.runtime.api.scheduler.Scheduler;
import org.mule.runtime.http.api.HttpConstants.Protocol;
import org.mule.runtime.http.api.server.HttpServer;
import org.mule.runtime.http.api.server.MethodRequestMatcher;
import org.mule.runtime.http.api.server.PathAndMethodRequestMatcher;
import org.mule.runtime.http.api.server.RequestHandler;
import org.mule.runtime.http.api.server.RequestHandlerManager;
import org.mule.runtime.http.api.server.ServerAddress;
import org.mule.service.http.impl.service.server.HttpListenerRegistry;

import java.io.IOException;
import java.util.Collection;
import java.util.concurrent.ExecutorService;
import java.util.function.Supplier;

import org.glassfish.grizzly.nio.transport.TCPNIOServerConnection;
import org.glassfish.grizzly.nio.transport.TCPNIOTransport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Grizzly based implementation of an {@link HttpServer}.
 */
public class GrizzlyHttpServer implements HttpServer, Supplier<ExecutorService> {

  protected static final Logger logger = LoggerFactory.getLogger(GrizzlyHttpServer.class);

  private final TCPNIOTransport transport;
  private final ServerAddress serverAddress;
  private final Protocol protocol;
  private final HttpListenerRegistry listenerRegistry;
  private TCPNIOServerConnection serverConnection;
  private Supplier<Scheduler> schedulerSource;
  private Runnable schedulerDisposer;
  private Scheduler scheduler;
  private boolean stopped = true;
  private boolean stopping;

  public GrizzlyHttpServer(ServerAddress serverAddress,
                           TCPNIOTransport transport,
                           HttpListenerRegistry listenerRegistry,
                           Supplier<Scheduler> schedulerSource,
                           Runnable schedulerDisposer,
                           Protocol protocol) {
    this.serverAddress = serverAddress;
    this.protocol = protocol;
    this.transport = transport;
    this.listenerRegistry = listenerRegistry;
    this.schedulerSource = schedulerSource;
    this.schedulerDisposer = schedulerDisposer;
  }

  @Override
  public synchronized HttpServer start() throws IOException {
    this.scheduler = schedulerSource != null ? schedulerSource.get() : null;
    serverConnection = transport.bind(serverAddress.getIp(), serverAddress.getPort());

    if (logger.isDebugEnabled()) {
      logger.debug(format("Listening for connections on '%s'", listenerUrl()));
    }

    serverConnection.addCloseListener((closeable, type) -> {
      try {
        scheduler.stop();
      } finally {
        scheduler = null;
      }
      schedulerDisposer.run();
    });
    stopped = false;
    return this;
  }

  @Override
  public synchronized HttpServer stop() {
    stopping = true;
    try {
      transport.unbind(serverConnection);

      if (logger.isDebugEnabled()) {
        logger.debug(format("Stopped listener on '%s'", listenerUrl()));
      }

      return this;
    } finally {
      stopping = false;
      stopped = true;
    }
  }

  @Override
  public void dispose() {
    // Nothing to do
  }

  @Override
  public ServerAddress getServerAddress() {
    return serverAddress;
  }

  @Override
  public Protocol getProtocol() {
    return protocol;
  }

  @Override
  public boolean isStopping() {
    return stopping;
  }

  @Override
  public boolean isStopped() {
    return stopped;
  }

  @Override
  public RequestHandlerManager addRequestHandler(Collection<String> methods, String path, RequestHandler requestHandler) {
    return this.listenerRegistry.addRequestHandler(this, requestHandler, PathAndMethodRequestMatcher.builder()
        .methodRequestMatcher(MethodRequestMatcher.builder(methods).build())
        .path(path)
        .build());
  }

  @Override
  public RequestHandlerManager addRequestHandler(String path, RequestHandler requestHandler) {
    return this.listenerRegistry.addRequestHandler(this, requestHandler, PathAndMethodRequestMatcher.builder()
        .methodRequestMatcher(acceptAll())
        .path(path)
        .build());
  }

  @Override
  public ExecutorService get() {
    return scheduler;
  }

  private String listenerUrl() {
    return format("%s://%s:%d", getProtocol().getScheme(), serverAddress.getIp(), serverAddress.getPort());
  }
}
