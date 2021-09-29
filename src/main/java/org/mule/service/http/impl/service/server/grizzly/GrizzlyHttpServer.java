/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.service.http.impl.service.server.grizzly;

import static java.lang.Math.min;
import static java.lang.String.format;
import static java.lang.System.getProperty;
import static java.lang.System.nanoTime;
import static java.lang.Thread.currentThread;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.NANOSECONDS;
import static org.mule.runtime.api.util.MuleSystemProperties.MULE_LOG_SEPARATION_DISABLED;
import static org.mule.runtime.core.api.util.ClassUtils.setContextClassLoader;
import static org.mule.runtime.http.api.server.MethodRequestMatcher.acceptAll;
import static org.mule.service.http.impl.service.server.grizzly.MuleSslFilter.createSslFilter;

import org.glassfish.grizzly.CloseType;
import org.glassfish.grizzly.nio.transport.TCPNIOConnection;
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
import org.mule.service.http.impl.service.server.HttpListenerRegistry;

import java.io.IOException;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SocketChannel;
import java.util.Collection;
import java.util.concurrent.ExecutorService;
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

  private volatile int openConnectionsCounter = 0;
  private final Object openConnectionsSync = new Object();
  private CountAcceptedConnectionsProbe acceptedConnectionsProbe;

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

    openConnectionsCounter = 0;

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
        synchronized (openConnectionsSync) {
          long remainingMillis = NANOSECONDS.toMillis(stopNanos - nanoTime());
          while (openConnectionsCounter != 0 && remainingMillis > 0) {
            long millisToWait = min(remainingMillis, 50);
            logger.debug("There are still {} open connections on server stop. Waiting {} milliseconds",
                         openConnectionsCounter, millisToWait);
            openConnectionsSync.wait(millisToWait);
            remainingMillis = NANOSECONDS.toMillis(stopNanos - nanoTime());
          }
          if (openConnectionsCounter != 0) {
            logger.warn("There are still {} open connections on server stop.", openConnectionsCounter);
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
      synchronized (openConnectionsSync) {
        openConnectionsCounter += 1;
      }

      // the server should close this connection when it is closed
      CloseListener<?, ?> closeAcceptedConnectionListener = new CloseAcceptedConnectionOnServerClose(clientConnection);
      serverConnection.addCloseListener(closeAcceptedConnectionListener);

      // the client should remove the listener from the server when it is closed
      clientConnection.addCloseListener(new RemoveCloseListenerOnClientClosed(serverConnection, closeAcceptedConnectionListener));

      clientConnection.addCloseListener((CloseListener) (closeable, iCloseType) -> {
        synchronized (openConnectionsSync) {
          openConnectionsCounter -= 1;
          if (openConnectionsCounter == 0) {
            openConnectionsSync.notifyAll();
          }
        }
      });
    }
  }

  private class OnCloseConnectionListener implements CloseListener<Connection<?>, CloseType> {

    /**
     * {@inheritDoc}
     */
    @Override
    public void onClosed(Connection closeable, CloseType type) throws IOException {
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

  private static class CloseAcceptedConnectionOnServerClose implements CloseListener<TCPNIOServerConnection, CloseType> {

    private final SocketChannel acceptedChannel;

    private CloseAcceptedConnectionOnServerClose(Connection<?> acceptedConnection) {
      this.acceptedChannel = getSocketChannel(acceptedConnection);
    }

    private static SocketChannel getSocketChannel(Connection<?> acceptedConnection) {
      if (!(acceptedConnection instanceof TCPNIOConnection)) {
        if (logger.isWarnEnabled()) {
          logger.warn("The accepted connection is not an instance of TCPNIOConnection");
        }
        return null;
      }

      SelectableChannel selectableChannel = ((TCPNIOConnection) acceptedConnection).getChannel();
      if (!(selectableChannel instanceof SocketChannel)) {
        if (logger.isWarnEnabled()) {
          logger.warn("The accepted connection doesn't hold a SocketChannel");
        }
        return null;
      }

      return (SocketChannel) selectableChannel;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onClosed(TCPNIOServerConnection closeable, CloseType type) throws IOException {
      if (acceptedChannel != null) {
        acceptedChannel.shutdownInput();
      }
    }
  }

  private static class RemoveCloseListenerOnClientClosed implements CloseListener<Connection<?>, CloseType> {

    private final CloseListener<?, ?> callbackToRemove;
    private final Connection<?> serverConnection;

    private RemoveCloseListenerOnClientClosed(Connection<?> serverConnection,
                                              CloseListener<?, ?> callbackToRemove) {
      this.serverConnection = serverConnection;
      this.callbackToRemove = callbackToRemove;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onClosed(Connection<?> closeable, CloseType type) throws IOException {
      if (serverConnection.isOpen()) {
        serverConnection.removeCloseListener(callbackToRemove);
      }
      closeable.removeCloseListener(this);
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
}
