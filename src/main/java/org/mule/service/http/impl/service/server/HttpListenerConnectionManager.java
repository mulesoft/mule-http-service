/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.service.http.impl.service.server;


import static java.lang.Integer.getInteger;
import static java.lang.Integer.max;
import static java.lang.Runtime.getRuntime;
import static java.lang.String.format;
import static org.mule.runtime.api.i18n.I18nMessageFactory.createStaticMessage;
import static org.mule.runtime.core.api.util.NetworkUtils.getLocalHostAddress;
import static org.mule.service.http.impl.config.ContainerTcpServerSocketProperties.loadTcpServerSocketProperties;
import static org.mule.service.http.impl.service.server.grizzly.IdleExecutor.IDLE_TIMEOUT_THREADS_PREFIX_NAME;
import org.mule.runtime.api.exception.MuleException;
import org.mule.runtime.api.lifecycle.Disposable;
import org.mule.runtime.api.lifecycle.Initialisable;
import org.mule.runtime.api.lifecycle.InitialisationException;
import org.mule.runtime.api.scheduler.Scheduler;
import org.mule.runtime.api.scheduler.SchedulerConfig;
import org.mule.runtime.api.scheduler.SchedulerService;
import org.mule.runtime.api.tls.TlsContextFactory;
import org.mule.runtime.http.api.server.HttpServer;
import org.mule.runtime.http.api.server.HttpServerConfiguration;
import org.mule.runtime.http.api.server.HttpServerFactory;
import org.mule.runtime.http.api.server.ServerAddress;
import org.mule.runtime.http.api.server.ServerAlreadyExistsException;
import org.mule.runtime.http.api.server.ServerCreationException;
import org.mule.runtime.http.api.server.ServerNotFoundException;
import org.mule.service.http.impl.service.server.grizzly.GrizzlyServerManager;

import java.net.UnknownHostException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

/**
 * Grizzly based {@link HttpServerFactory}.
 *
 * @since 1.0
 */
public class HttpListenerConnectionManager implements ContextHttpServerFactory, Initialisable, Disposable {

  protected static final int DEFAULT_SELECTOR_THREAD_COUNT =
      getInteger(HttpListenerConnectionManager.class.getName() + ".DEFAULT_SELECTOR_THREAD_COUNT",
                 max(getRuntime().availableProcessors(), 2));
  private static final String LISTENER_THREAD_NAME_PREFIX = "http.listener";

  private final SchedulerService schedulerService;
  private final SchedulerConfig schedulersConfig;
  protected Scheduler selectorScheduler;
  protected Scheduler workerScheduler;
  protected Scheduler idleTimeoutScheduler;
  protected final HttpListenerRegistry httpListenerRegistry = new HttpListenerRegistry();
  private HttpServerManager httpServerManager;

  private AtomicBoolean initialized = new AtomicBoolean(false);

  public HttpListenerConnectionManager(SchedulerService schedulerService, SchedulerConfig schedulersConfig) {
    this.schedulerService = schedulerService;
    this.schedulersConfig = schedulersConfig;
  }

  @Override
  public void initialise() throws InitialisationException {
    if (initialized.getAndSet(true)) {
      return;
    }

    selectorScheduler = schedulerService.customScheduler(schedulersConfig.withMaxConcurrentTasks(DEFAULT_SELECTOR_THREAD_COUNT)
        .withName(LISTENER_THREAD_NAME_PREFIX), 0);
    workerScheduler = schedulerService.ioScheduler(schedulersConfig);
    idleTimeoutScheduler =
        schedulerService.ioScheduler(schedulersConfig.withName(LISTENER_THREAD_NAME_PREFIX + IDLE_TIMEOUT_THREADS_PREFIX_NAME));
    httpServerManager = createServerManager();
  }

  @Override
  public synchronized void dispose() {
    httpServerManager.dispose();
    idleTimeoutScheduler.stop();
    workerScheduler.stop();
    selectorScheduler.stop();
  }

  @Override
  public HttpServer create(HttpServerConfiguration configuration, String context) throws ServerCreationException {
    ServerAddress serverAddress;
    String host = configuration.getHost();
    try {
      serverAddress = createServerAddress(host, configuration.getPort());
    } catch (UnknownHostException e) {
      throw new ServerCreationException(format("Cannot resolve host %s", host), e);
    }

    TlsContextFactory tlsContextFactory = configuration.getTlsContextFactory();
    HttpServer httpServer;
    if (tlsContextFactory == null) {
      httpServer = createServer(serverAddress, configuration.getSchedulerSupplier(), configuration.isUsePersistentConnections(),
                                configuration.getConnectionIdleTimeout(), new ServerIdentifier(context, configuration.getName()),
                                configuration.getReadTimeout());
    } else {
      httpServer = createSslServer(serverAddress, tlsContextFactory, configuration.getSchedulerSupplier(),
                                   configuration.isUsePersistentConnections(), configuration.getConnectionIdleTimeout(),
                                   new ServerIdentifier(context, configuration.getName()),
                                   configuration.getReadTimeout());
    }

    return httpServer;
  }

  @Override
  public HttpServer lookup(ServerIdentifier identifier) throws ServerNotFoundException {
    return httpServerManager.lookupServer(identifier);
  }

  public HttpServer createServer(ServerAddress serverAddress,
                                 Supplier<Scheduler> schedulerSupplier, boolean usePersistentConnections,
                                 int connectionIdleTimeout, ServerIdentifier identifier, long readTimeout)
      throws ServerCreationException {
    if (!containsServerFor(serverAddress, identifier)) {
      return httpServerManager.createServerFor(serverAddress, schedulerSupplier, usePersistentConnections,
                                               connectionIdleTimeout, identifier, readTimeout);
    } else {
      throw new ServerAlreadyExistsException(serverAddress);
    }
  }

  public boolean containsServerFor(ServerAddress serverAddress, ServerIdentifier identifier) {
    return httpServerManager.containsServerFor(serverAddress, identifier);
  }

  public HttpServer createSslServer(ServerAddress serverAddress, TlsContextFactory tlsContext,
                                    Supplier<Scheduler> schedulerSupplier, boolean usePersistentConnections,
                                    int connectionIdleTimeout, ServerIdentifier identifier, long readTimeout)
      throws ServerCreationException {
    if (!containsServerFor(serverAddress, identifier)) {
      return httpServerManager.createSslServerFor(tlsContext, schedulerSupplier, serverAddress, usePersistentConnections,
                                                  connectionIdleTimeout, identifier, readTimeout);
    } else {
      throw new ServerAlreadyExistsException(serverAddress);
    }
  }

  protected GrizzlyServerManager createServerManager() throws InitialisationException {
    try {
      return new GrizzlyServerManager(selectorScheduler, workerScheduler, idleTimeoutScheduler, httpListenerRegistry,
                                      loadTcpServerSocketProperties(), DEFAULT_SELECTOR_THREAD_COUNT);
    } catch (MuleException e) {
      throw new InitialisationException(createStaticMessage("Could not load server socket properties."), e, this);
    }
  }

  /**
   * Creates the server address object with the IP and port that a server should bind to.
   */
  private ServerAddress createServerAddress(String host, int port) throws UnknownHostException {
    return new DefaultServerAddress(getLocalHostAddress(host), port);
  }

}
