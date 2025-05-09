/*
 * Copyright 2023 Salesforce, Inc. All rights reserved.
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.service.http.impl.service.server.grizzly;

import static org.mule.runtime.api.util.MuleSystemProperties.MULE_LOG_SEPARATION_DISABLED;
import static org.mule.runtime.core.api.util.ClassUtils.setContextClassLoader;
import static org.mule.runtime.http.api.server.MethodRequestMatcher.acceptAll;
import static org.mule.service.http.impl.service.server.grizzly.MuleSslFilter.createSslFilter;

import static java.lang.Math.min;
import static java.lang.String.format;
import static java.lang.System.getProperty;
import static java.lang.System.nanoTime;
import static java.lang.Thread.currentThread;
import static java.util.Collections.synchronizedList;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.NANOSECONDS;

import org.mule.runtime.api.scheduler.Scheduler;
import org.mule.runtime.api.tls.TlsContextFactory;
import org.mule.runtime.http.api.HttpConstants.Protocol;
import org.mule.runtime.http.api.domain.request.HttpRequestContext;
import org.mule.runtime.http.api.server.HttpServer;
import org.mule.runtime.http.api.server.MethodRequestMatcher;
import org.mule.runtime.http.api.server.PathAndMethodRequestMatcher;
import org.mule.runtime.http.api.server.RequestHandler;
import org.mule.runtime.http.api.server.RequestHandlerManager;
import org.mule.runtime.http.api.server.ServerAddress;
import org.mule.runtime.http.api.server.async.HttpResponseReadyCallback;
import org.mule.runtime.http.api.sse.server.SseClient;
import org.mule.runtime.http.api.sse.server.SseEndpointManager;
import org.mule.runtime.http.api.sse.server.SseRequestContext;
import org.mule.service.http.common.server.sse.SseHandlerManagerAdapter;
import org.mule.service.http.common.server.sse.SseRequestHandler;
import org.mule.service.http.impl.service.server.HttpListenerRegistry;

import java.io.IOException;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.glassfish.grizzly.CloseListener;
import org.glassfish.grizzly.Connection;
import org.glassfish.grizzly.ConnectionProbe;
import org.glassfish.grizzly.nio.transport.TCPNIOServerConnection;
import org.glassfish.grizzly.nio.transport.TCPNIOTransport;
import org.glassfish.grizzly.ssl.SSLFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Grizzly based implementation of an {@link HttpServer}.
 */
public class GrizzlyHttpServer implements HttpServer, Supplier<ExecutorService> {

  protected static final Logger logger = LoggerFactory.getLogger(GrizzlyHttpServer.class);
  private static boolean REPLACE_CONTEXT_CLASSLOADER = getProperty(MULE_LOG_SEPARATION_DISABLED) == null;

  private final TCPNIOTransport transport;
  private final ServerAddress serverAddress;
  private final HttpListenerRegistry listenerRegistry;
  private TCPNIOServerConnection serverConnection;
  private GrizzlyAddressFilter<SSLFilter> sslFilter;
  private Supplier<Scheduler> schedulerSource;
  private Runnable schedulerDisposer;
  private Scheduler scheduler;
  private boolean stopped = true;
  private boolean stopping;
  private Supplier<Long> shutdownTimeoutSupplier;

  private CountAcceptedConnectionsProbe acceptedConnectionsProbe;

  /** Used to track client connections so we know if we have to wait on stop. */
  private final List<Connection<?>> clientConnections = synchronizedList(new LinkedList<>());

  public GrizzlyHttpServer(ServerAddress serverAddress,
                           TCPNIOTransport transport,
                           HttpListenerRegistry listenerRegistry,
                           Supplier<Scheduler> schedulerSource,
                           Runnable schedulerDisposer,
                           GrizzlyAddressFilter<SSLFilter> sslFilter,
                           Supplier<Long> shutdownTimeoutSupplier) {
    this.serverAddress = serverAddress;
    this.transport = transport;
    this.listenerRegistry = listenerRegistry;
    this.schedulerSource = schedulerSource;
    this.schedulerDisposer = schedulerDisposer;
    this.sslFilter = sslFilter;
    this.shutdownTimeoutSupplier = shutdownTimeoutSupplier;
  }

  @Override
  public synchronized HttpServer start() throws IOException {
    this.scheduler = schedulerSource != null ? schedulerSource.get() : null;
    serverConnection = transport.bind(serverAddress.getIp(), serverAddress.getPort());
    acceptedConnectionsProbe = new CountAcceptedConnectionsProbe();
    serverConnection.getMonitoringConfig().addProbes(acceptedConnectionsProbe);

    if (logger.isInfoEnabled()) {
      logger.info("Listening for connections on '{}'", listenerUrl());
    }

    serverConnection.addCloseListener(new OnCloseConnectionListener());
    stopped = false;
    return this;
  }

  @Override
  public synchronized HttpServer stop() {
    if (stopped) {
      return this;
    }

    Long shutdownTimeout = shutdownTimeoutSupplier.get();
    final long stopNanos = nanoTime() + MILLISECONDS.toNanos(shutdownTimeout);

    stopping = true;
    try {
      transport.unbind(serverConnection);

      if (shutdownTimeout != 0) {
        synchronized (clientConnections) {
          long remainingMillis = NANOSECONDS.toMillis(stopNanos - nanoTime());
          while (!clientConnections.isEmpty() && remainingMillis > 0) {
            long millisToWait = min(remainingMillis, 50);
            logger.debug("There are still {} open connections on server stop. Waiting {} milliseconds",
                         clientConnections.size(), millisToWait);
            clientConnections.wait(millisToWait);
            remainingMillis = NANOSECONDS.toMillis(stopNanos - nanoTime());
          }

          if (!clientConnections.isEmpty()) {
            logger.warn("There are still {} open connections on server stop.", clientConnections.size());
          }
        }
      }

      if (logger.isInfoEnabled()) {
        logger.info("Stopped listener on '{}'", listenerUrl());
      }
    } catch (InterruptedException e) {
      currentThread().interrupt();
    } finally {
      stopped = true;
      stopping = false;
    }
    return this;
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
    return sslFilter.hasFilterForAddress(getServerAddress()) ? Protocol.HTTPS : Protocol.HTTP;
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
    return this.listenerRegistry.addRequestHandler(this, preservingTCCL(requestHandler), PathAndMethodRequestMatcher.builder()
        .methodRequestMatcher(MethodRequestMatcher.builder(methods).build())
        .path(path)
        .build());
  }

  @Override
  public RequestHandlerManager addRequestHandler(String path, RequestHandler requestHandler) {
    return this.listenerRegistry.addRequestHandler(this, preservingTCCL(requestHandler), PathAndMethodRequestMatcher.builder()
        .methodRequestMatcher(acceptAll())
        .path(path)
        .build());
  }

  @Override
  public SseEndpointManager sse(String ssePath,
                                Consumer<SseRequestContext> onRequest,
                                Consumer<SseClient> onClient) {
    return new SseHandlerManagerAdapter(addRequestHandler(ssePath, new SseRequestHandler(onRequest, onClient)));
  }

  private RequestHandler preservingTCCL(final RequestHandler requestHandler) {
    final ClassLoader creationClassLoader = currentThread().getContextClassLoader();
    return new RequestHandler() {

      @Override
      public void handleRequest(HttpRequestContext requestContext, HttpResponseReadyCallback responseCallback) {
        ClassLoader outerClassLoader = currentThread().getContextClassLoader();
        ClassLoader innerClassLoader = getContextClassLoader();
        setContextClassLoader(currentThread(), outerClassLoader, innerClassLoader);
        try {
          requestHandler.handleRequest(requestContext, responseCallback);
        } finally {
          setContextClassLoader(currentThread(), innerClassLoader, outerClassLoader);
        }
      }

      @Override
      public ClassLoader getContextClassLoader() {
        return REPLACE_CONTEXT_CLASSLOADER ? creationClassLoader : requestHandler.getContextClassLoader();
      }
    };
  }

  @Override
  public ExecutorService get() {
    return scheduler;
  }

  @Override
  public void enableTls(TlsContextFactory tlsContextFactory) {
    sslFilter.addFilterForAddress(getServerAddress(), createSslFilter(tlsContextFactory));
  }

  @Override
  public void disableTls() {
    sslFilter.removeFilterForAddress(getServerAddress());
  }

  private String listenerUrl() {
    return format("%s://%s:%d", getProtocol().getScheme(), serverAddress.getIp(), serverAddress.getPort());
  }

  private class CountAcceptedConnectionsProbe extends ConnectionProbe.Adapter {

    /**
     * {@inheritDoc}
     */
    @Override
    public void onAcceptEvent(Connection serverConnection, Connection clientConnection) {
      clientConnections.add(clientConnection);
      clientConnection.addCloseListener((CloseListener) (closeable, iCloseType) -> {
        clientConnections.remove(clientConnection);
        if (clientConnections.isEmpty()) {
          synchronized (clientConnections) {
            if (clientConnections.isEmpty()) {
              clientConnections.notifyAll();
            }
          }
        }
      });
    }
  }

  private class OnCloseConnectionListener implements Connection.CloseListener {

    /**
     * {@inheritDoc}
     */
    @Override
    public void onClosed(Connection closeable, Connection.CloseType type) throws IOException {
      try {
        if (scheduler != null) {
          scheduler.stop();
        }
      } finally {
        scheduler = null;
        schedulerDisposer.run();
        closeable.removeCloseListener(this);
        serverConnection.getMonitoringConfig().removeProbes(acceptedConnectionsProbe);
        acceptedConnectionsProbe = null;
      }
    }
  }

  /**
   *
   * @param replaceContextClassloader
   *
   * @deprecated Used only for testing
   */
  public static void setReplaceCtxClassloader(boolean replaceContextClassloader) {
    REPLACE_CONTEXT_CLASSLOADER = replaceContextClassloader;
  }

  /**
   * @deprecated Used only for testing
   */
  @Deprecated
  public static void refreshSystemProperties() {
    REPLACE_CONTEXT_CLASSLOADER = getProperty(MULE_LOG_SEPARATION_DISABLED) == null;
  }
}
