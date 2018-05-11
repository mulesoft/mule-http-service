/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.service.http.impl.service.server.grizzly;

import static java.lang.Integer.getInteger;
import static java.lang.Integer.valueOf;
import static java.lang.String.format;
import static java.lang.System.currentTimeMillis;
import static java.lang.System.getProperty;
import static java.lang.Thread.currentThread;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.glassfish.grizzly.http.HttpCodecFilter.DEFAULT_MAX_HTTP_PACKET_HEADER_SIZE;
import static org.mule.runtime.api.i18n.I18nMessageFactory.createStaticMessage;
import static org.mule.runtime.core.api.config.MuleProperties.SYSTEM_PROPERTY_PREFIX;
import static org.mule.runtime.core.api.util.ClassUtils.withContextClassLoader;
import static org.mule.runtime.http.api.HttpConstants.Protocol.HTTP;
import static org.mule.runtime.http.api.HttpConstants.Protocol.HTTPS;
import static org.mule.service.http.impl.service.HttpMessageLogger.LoggerType.LISTENER;
import static org.slf4j.LoggerFactory.getLogger;
import org.mule.runtime.api.exception.MuleRuntimeException;
import org.mule.runtime.api.scheduler.Scheduler;
import org.mule.runtime.api.tls.TlsContextFactory;
import org.mule.runtime.http.api.HttpConstants.Protocol;
import org.mule.runtime.http.api.server.HttpServer;
import org.mule.runtime.http.api.server.RequestHandler;
import org.mule.runtime.http.api.server.RequestHandlerManager;
import org.mule.runtime.http.api.server.ServerAddress;
import org.mule.runtime.http.api.server.ServerAlreadyExistsException;
import org.mule.runtime.http.api.server.ServerCreationException;
import org.mule.runtime.http.api.server.ServerNotFoundException;
import org.mule.runtime.http.api.tcp.TcpServerSocketProperties;
import org.mule.service.http.impl.service.HttpMessageLogger;
import org.mule.service.http.impl.service.server.HttpListenerRegistry;
import org.mule.service.http.impl.service.server.HttpServerManager;
import org.mule.service.http.impl.service.server.ServerIdentifier;

import java.io.IOException;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.function.Supplier;

import org.glassfish.grizzly.filterchain.FilterChainBuilder;
import org.glassfish.grizzly.filterchain.TransportFilter;
import org.glassfish.grizzly.http.HttpServerFilter;
import org.glassfish.grizzly.http.KeepAlive;
import org.glassfish.grizzly.nio.RoundRobinConnectionDistributor;
import org.glassfish.grizzly.nio.transport.TCPNIOTransport;
import org.glassfish.grizzly.nio.transport.TCPNIOTransportBuilder;
import org.glassfish.grizzly.ssl.SSLEngineConfigurator;
import org.glassfish.grizzly.ssl.SSLFilter;
import org.glassfish.grizzly.utils.DelayedExecutor;
import org.glassfish.grizzly.utils.IdleTimeoutFilter;
import org.slf4j.Logger;

public class GrizzlyServerManager implements HttpServerManager {

  private static final Logger LOGGER = getLogger(GrizzlyServerManager.class);

  // Defines the maximum size in bytes accepted for the http request header section (request line + headers)
  public static final String MAXIMUM_HEADER_SECTION_SIZE_PROPERTY_KEY = SYSTEM_PROPERTY_PREFIX + "http.headerSectionSize";
  private static final int MAX_KEEP_ALIVE_REQUESTS = -1;
  // We use this default for all connections but each server can define it's idle timeout to override this
  private static final int DEFAULT_SERVER_TIMEOUT_MILLIS = 60000;
  // Defines the delay between the server timeout and the connection timeout when the latter forces it
  private static final int SERVER_TIMEOUT_DELAY_MILLIS = 500;
  // Only use a dedicated selector as an acceptor when there are more than 4 or more selectors. A dedicated acceptor makes HTTP
  // listener more responsive to new connections but can impact throughput if the acceptor:selector ratio is too high. This
  // ensures the minimum ratio is 1:3.
  private static final int MIN_SELECTORS_FOR_DEDICATED_ACCEPTOR =
      getInteger(GrizzlyServerManager.class.getName() + ".MIN_SELECTORS_FOR_DEDICATED_ACCEPTOR", 4);

  private static final long DISPOSE_TIMEOUT_MILLIS = 30000;

  private final GrizzlyAddressDelegateFilter<IdleTimeoutFilter> timeoutFilterDelegate;
  private final GrizzlyAddressDelegateFilter<SSLFilter> sslFilterDelegate;
  private final GrizzlyAddressDelegateFilter<HttpServerFilter> httpServerFilterDelegate;
  private final TCPNIOTransport transport;
  private final GrizzlyRequestDispatcherFilter requestHandlerFilter;
  private final HttpListenerRegistry httpListenerRegistry;
  private final WorkManagerSourceExecutorProvider executorProvider;
  private final ExecutorService idleTimeoutExecutorService;
  private Map<ServerAddress, GrizzlyHttpServer> servers = new ConcurrentHashMap<>();
  private Map<ServerIdentifier, GrizzlyHttpServer> serversByIdentifier = new ConcurrentHashMap<>();
  private Map<ServerAddress, IdleExecutor> idleExecutorPerServerAddressMap = new ConcurrentHashMap<>();

  private boolean transportStarted;
  private int serverTimeout;

  public GrizzlyServerManager(ExecutorService selectorPool, ExecutorService workerPool,
                              ExecutorService idleTimeoutExecutorService, HttpListenerRegistry httpListenerRegistry,
                              TcpServerSocketProperties serverSocketProperties, int selectorCount) {
    this.httpListenerRegistry = httpListenerRegistry;
    //TODO - MULE-14960: Remove system property once this can be configured through a file
    this.serverTimeout = getInteger("mule.http.server.timeout", DEFAULT_SERVER_TIMEOUT_MILLIS);
    requestHandlerFilter = new GrizzlyRequestDispatcherFilter(httpListenerRegistry);
    timeoutFilterDelegate = new GrizzlyAddressDelegateFilter<>();
    sslFilterDelegate = new GrizzlyAddressDelegateFilter<>();
    httpServerFilterDelegate = new GrizzlyAddressDelegateFilter<>();

    FilterChainBuilder serverFilterChainBuilder = FilterChainBuilder.stateless();
    serverFilterChainBuilder.add(new TransportFilter());
    serverFilterChainBuilder.add(timeoutFilterDelegate);
    serverFilterChainBuilder.add(sslFilterDelegate);
    serverFilterChainBuilder.add(httpServerFilterDelegate);
    serverFilterChainBuilder.add(requestHandlerFilter);

    // Initialize Transport
    executorProvider = new WorkManagerSourceExecutorProvider();
    TCPNIOTransportBuilder transportBuilder = TCPNIOTransportBuilder.newInstance().setOptimizedForMultiplexing(true)
        .setIOStrategy(new ExecutorPerServerAddressIOStrategy(executorProvider));

    configureServerSocketProperties(transportBuilder, serverSocketProperties);

    transport = transportBuilder.build();

    transport
        .setNIOChannelDistributor(new RoundRobinConnectionDistributor(transport,
                                                                      selectorCount >= MIN_SELECTORS_FOR_DEDICATED_ACCEPTOR,
                                                                      true));

    transport.setSelectorRunnersCount(selectorCount);
    transport.setWorkerThreadPool(workerPool);
    transport.setKernelThreadPool(selectorPool);

    // Set filterchain as a Transport Processor
    transport.setProcessor(serverFilterChainBuilder.build());

    this.idleTimeoutExecutorService = idleTimeoutExecutorService;
  }

  private void configureServerSocketProperties(TCPNIOTransportBuilder transportBuilder,
                                               TcpServerSocketProperties serverSocketProperties) {
    if (serverSocketProperties.getKeepAlive() != null) {
      transportBuilder.setKeepAlive(serverSocketProperties.getKeepAlive());
    }
    if (serverSocketProperties.getLinger() != null) {
      transportBuilder.setLinger(serverSocketProperties.getLinger());
    }

    if (serverSocketProperties.getReceiveBufferSize() != null) {
      transportBuilder.setReadBufferSize(serverSocketProperties.getReceiveBufferSize());
    }

    if (serverSocketProperties.getSendBufferSize() != null) {
      transportBuilder.setWriteBufferSize(serverSocketProperties.getSendBufferSize());
    }

    if (serverSocketProperties.getClientTimeout() != null) {
      transportBuilder.setClientSocketSoTimeout(serverSocketProperties.getClientTimeout());
    }

    Integer serverTimeout = serverSocketProperties.getServerTimeout();
    if (serverTimeout != null) {
      transportBuilder.setServerSocketSoTimeout(serverTimeout);
      this.serverTimeout = serverTimeout;
    }

    transportBuilder.setReuseAddress(serverSocketProperties.getReuseAddress());
    transportBuilder.setTcpNoDelay(serverSocketProperties.getSendTcpNoDelay());
    transportBuilder.setServerConnectionBackLog(serverSocketProperties.getReceiveBacklog());
  }

  /**
   * Starts the transport if not started. This is because it should be started lazily when the first server is registered
   * (otherwise there will be Grizzly threads even if there is no HTTP usage in any app).
   */
  private void startTransportIfNotStarted() throws ServerCreationException {
    withContextClassLoader(this.getClass().getClassLoader(), () -> {
      if (!transportStarted) {
        transportStarted = true;
        transport.start();
      }
      return null;
    }, ServerCreationException.class, e -> {
      throw new ServerCreationException("Transport failed at startup.", e);
    });
  }

  @Override
  public boolean containsServerFor(final ServerAddress serverAddress, ServerIdentifier identifier) {
    return servers.containsKey(serverAddress) || containsOverlappingServerFor(serverAddress) ||
        serversByIdentifier.containsKey(identifier);
  }

  private boolean containsOverlappingServerFor(ServerAddress newServerAddress) {
    for (ServerAddress serverAddress : servers.keySet()) {
      if (serverAddress.overlaps(newServerAddress)) {
        return true;
      }
    }
    return false;
  }

  @Override
  public HttpServer createSslServerFor(TlsContextFactory tlsContextFactory, Supplier<Scheduler> schedulerSupplier,
                                       final ServerAddress serverAddress, boolean usePersistentConnections,
                                       int connectionIdleTimeout, ServerIdentifier identifier)
      throws ServerCreationException {
    LOGGER.debug("Creating https server socket for ip {} and port {}", serverAddress.getIp(), serverAddress.getPort());
    if (servers.containsKey(serverAddress)) {
      throw new ServerAlreadyExistsException(serverAddress);
    }
    startTransportIfNotStarted();
    DelayedExecutor delayedExecutor = createAndGetDelayedExecutor(serverAddress);
    timeoutFilterDelegate.addFilterForAddress(serverAddress,
                                              createTimeoutFilter(usePersistentConnections, connectionIdleTimeout,
                                                                  delayedExecutor));
    sslFilterDelegate.addFilterForAddress(serverAddress, createSslFilter(tlsContextFactory));
    httpServerFilterDelegate
        .addFilterForAddress(serverAddress,
                             createHttpServerFilter(connectionIdleTimeout, usePersistentConnections, delayedExecutor));
    final GrizzlyHttpServer grizzlyServer = new ManagedGrizzlyHttpServer(serverAddress, transport, httpListenerRegistry,
                                                                         schedulerSupplier,
                                                                         () -> executorProvider.removeExecutor(serverAddress),
                                                                         identifier, HTTPS);
    executorProvider.addExecutor(serverAddress, grizzlyServer);
    servers.put(serverAddress, grizzlyServer);
    serversByIdentifier.put(identifier, grizzlyServer);
    return grizzlyServer;
  }

  @Override
  public HttpServer createServerFor(ServerAddress serverAddress, Supplier<Scheduler> schedulerSupplier,
                                    boolean usePersistentConnections, int connectionIdleTimeout, ServerIdentifier identifier)
      throws ServerCreationException {
    LOGGER.debug("Creating http server socket for ip {} and port {}", serverAddress.getIp(), serverAddress.getPort());
    if (servers.containsKey(serverAddress)) {
      throw new ServerAlreadyExistsException(serverAddress);
    }
    startTransportIfNotStarted();
    DelayedExecutor delayedExecutor = createAndGetDelayedExecutor(serverAddress);
    timeoutFilterDelegate.addFilterForAddress(serverAddress,
                                              createTimeoutFilter(usePersistentConnections, connectionIdleTimeout,
                                                                  delayedExecutor));
    httpServerFilterDelegate
        .addFilterForAddress(serverAddress,
                             createHttpServerFilter(connectionIdleTimeout, usePersistentConnections, delayedExecutor));
    final GrizzlyHttpServer grizzlyServer = new ManagedGrizzlyHttpServer(serverAddress, transport, httpListenerRegistry,
                                                                         schedulerSupplier,
                                                                         () -> executorProvider.removeExecutor(serverAddress),
                                                                         identifier, HTTP);
    executorProvider.addExecutor(serverAddress, grizzlyServer);
    servers.put(serverAddress, grizzlyServer);
    serversByIdentifier.put(identifier, grizzlyServer);
    return grizzlyServer;
  }

  @Override
  public HttpServer lookupServer(ServerIdentifier identifier) throws ServerNotFoundException {
    GrizzlyHttpServer httpServer = serversByIdentifier.get(identifier);
    if (httpServer != null) {
      return new NoLifecycleHttpServer(httpServer);
    } else {
      throw new ServerNotFoundException(identifier.getName());
    }
  }

  @Override
  public void dispose() {
    if (transportStarted) {
      transport.shutdown();
      servers.clear();
      serversByIdentifier.clear();
    }
  }

  private IdleTimeoutFilter createTimeoutFilter(boolean usePersistentConnections, int connectionIdleTimeout,
                                                DelayedExecutor delayedExecutor) {
    int timeout = serverTimeout;
    if (usePersistentConnections && serverTimeout < connectionIdleTimeout) {
      // Avoid interfering with the keep alive timeout but secure plain TCP connections
      timeout = connectionIdleTimeout + SERVER_TIMEOUT_DELAY_MILLIS;
    }
    return new IdleTimeoutFilter(delayedExecutor, timeout, MILLISECONDS);
  }

  private SSLFilter createSslFilter(final TlsContextFactory tlsContextFactory) {
    try {
      boolean clientAuth = tlsContextFactory.isTrustStoreConfigured();
      final SSLEngineConfigurator serverConfig =
          new SSLEngineConfigurator(tlsContextFactory.createSslContext(), false, clientAuth, false);
      final String[] enabledProtocols = tlsContextFactory.getEnabledProtocols();
      if (enabledProtocols != null) {
        serverConfig.setEnabledProtocols(enabledProtocols);
      }
      final String[] enabledCipherSuites = tlsContextFactory.getEnabledCipherSuites();
      if (enabledCipherSuites != null) {
        serverConfig.setEnabledCipherSuites(enabledCipherSuites);
      }
      final SSLEngineConfigurator clientConfig = serverConfig.copy().setClientMode(true);
      return new MuleSslFilter(serverConfig, clientConfig);
    } catch (Exception e) {
      throw new MuleRuntimeException(e);
    }
  }

  private HttpServerFilter createHttpServerFilter(int connectionIdleTimeout, boolean usePersistentConnections,
                                                  DelayedExecutor delayedExecutor) {
    KeepAlive ka = null;
    if (usePersistentConnections) {
      ka = new KeepAlive();
      ka.setMaxRequestsCount(MAX_KEEP_ALIVE_REQUESTS);
      ka.setIdleTimeoutInSeconds((int) MILLISECONDS.toSeconds(connectionIdleTimeout));
    }
    HttpServerFilter httpServerFilter =
        new HttpServerFilter(true, retrieveMaximumHeaderSectionSize(), ka, delayedExecutor);
    httpServerFilter.getMonitoringConfig().addProbes(new HttpMessageLogger(LISTENER, currentThread().getContextClassLoader()));
    httpServerFilter.setAllowPayloadForUndefinedHttpMethods(true);
    return httpServerFilter;
  }

  private DelayedExecutor createAndGetDelayedExecutor(ServerAddress serverAddress) {
    IdleExecutor idleExecutor = new IdleExecutor(idleTimeoutExecutorService);
    idleExecutorPerServerAddressMap.put(serverAddress, idleExecutor);
    return idleExecutor.getIdleTimeoutDelayedExecutor();
  }

  private int retrieveMaximumHeaderSectionSize() {
    try {
      return valueOf(getProperty(MAXIMUM_HEADER_SECTION_SIZE_PROPERTY_KEY, String.valueOf(DEFAULT_MAX_HTTP_PACKET_HEADER_SIZE)));
    } catch (NumberFormatException e) {
      throw new MuleRuntimeException(createStaticMessage(format("Invalid value %s for %s configuration",
                                                                getProperty(MAXIMUM_HEADER_SECTION_SIZE_PROPERTY_KEY),
                                                                MAXIMUM_HEADER_SECTION_SIZE_PROPERTY_KEY)),
                                     e);
    }
  }

  /**
   * Wrapper that adds startup and disposal of manager specific data.
   */
  private class ManagedGrizzlyHttpServer extends GrizzlyHttpServer {

    private final ServerIdentifier identifier;

    // TODO - MULE-11117: Cleanup GrizzlyServerManager server specific data

    public ManagedGrizzlyHttpServer(ServerAddress serverAddress, TCPNIOTransport transport,
                                    HttpListenerRegistry listenerRegistry, Supplier<Scheduler> schedulerSupplier,
                                    Runnable schedulerDisposer, ServerIdentifier identifier, Protocol protocol) {
      super(serverAddress, transport, listenerRegistry, schedulerSupplier, schedulerDisposer, protocol);
      this.identifier = identifier;
    }

    @Override
    public synchronized HttpServer start() throws IOException {
      idleExecutorPerServerAddressMap.get(this.getServerAddress()).start();
      return super.start();
    }

    @Override
    public synchronized void dispose() {
      super.dispose();
      ServerAddress serverAddress = this.getServerAddress();
      servers.remove(serverAddress);
      serversByIdentifier.remove(identifier);
      httpListenerRegistry.removeHandlersFor(this);

      long startTime = currentTimeMillis();
      while (requestHandlerFilter.activeRequestsFor(serverAddress) > 0) {
        if (currentTimeMillis() > startTime + DISPOSE_TIMEOUT_MILLIS) {
          LOGGER.warn("Dispose of http server for {} timed out.", serverAddress);
          break;
        }

        try {
          Thread.sleep(50);
        } catch (InterruptedException e) {
          // Reset interrupt flag and continue with the disposal
          currentThread().interrupt();
          break;
        }
      }

      httpServerFilterDelegate.removeFilterForAddress(serverAddress);
      sslFilterDelegate.removeFilterForAddress(serverAddress);
      timeoutFilterDelegate.removeFilterForAddress(serverAddress);
      idleExecutorPerServerAddressMap.get(serverAddress).dispose();
      idleExecutorPerServerAddressMap.remove(serverAddress);
    }
  }


  /**
   * Wrapper that avoids all lifecycle operations, so that the inner server owns that exclusively.
   */
  private class NoLifecycleHttpServer implements HttpServer {

    private final HttpServer delegate;

    public NoLifecycleHttpServer(HttpServer delegate) {
      this.delegate = delegate;
    }

    @Override
    public HttpServer start() throws IOException {
      return this;
    }

    @Override
    public HttpServer stop() {
      return this;
    }

    @Override
    public void dispose() {
      // Do nothing
    }

    @Override
    public ServerAddress getServerAddress() {
      return delegate.getServerAddress();
    }

    @Override
    public Protocol getProtocol() {
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
    public RequestHandlerManager addRequestHandler(Collection<String> methods, String path,
                                                   RequestHandler requestHandler) {
      return delegate.addRequestHandler(methods, path, requestHandler);
    }

    @Override
    public RequestHandlerManager addRequestHandler(String path, RequestHandler requestHandler) {
      return delegate.addRequestHandler(path, requestHandler);
    }
  }

}
