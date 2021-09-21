/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.service.http.impl.service.server;

import org.mule.runtime.api.message.Message;
import org.mule.runtime.api.scheduler.Scheduler;
import org.mule.runtime.api.tls.TlsContextFactory;
import org.mule.runtime.http.api.server.HttpServer;
import org.mule.runtime.http.api.server.ServerAddress;
import org.mule.runtime.http.api.server.ServerCreationException;
import org.mule.runtime.http.api.server.ServerNotFoundException;

import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

/**
 * A Server managers is in charge to handle all ServerSocket connections and to handle incoming requests to an Execution Service
 * for processing.
 *
 * @since 1.0
 */
public interface HttpServerManager {

  /**
   * @param serverAddress address of the server
   * @param identifier    the id of the server
   * @return true if there's already a server created for that port and either the same host or an overlapping one (0.0.0.0 or any
   *         other if the serverAddress host is 0.0.0.0), false otherwise.
   */
  boolean containsServerFor(ServerAddress serverAddress, ServerIdentifier identifier);

  /**
   *
   * @param serverAddress            address of the server
   * @param schedulerSupplier        work manager source to use for retrieving an {@link Executor} for processing this server
   *                                 requests
   * @param usePersistentConnections if true, the connections will be kept open for subsequent requests
   * @param connectionIdleTimeout    the amount of milliseconds to keep open an idle connection @return the create Server handler
   * @param identifier               the id of the server
   * @param shutdownTimeout          time to wait for persistent connections to be closed when server is stopped.
   * @throws ServerCreationException if it was not possible to create the Server. Most likely because the host and port is already
   *                                 in use.
   * @deprecated as of 1.7.0, 1.6.1, 1.5.15 use
   *             {@link #createServerFor(ServerAddress, Supplier, boolean, int, ServerIdentifier, Supplier, long)} instead.
   */
  @Deprecated
  HttpServer createServerFor(ServerAddress serverAddress, Supplier<Scheduler> schedulerSupplier,
                             boolean usePersistentConnections, int connectionIdleTimeout, ServerIdentifier identifier,
                             Supplier<Long> shutdownTimeout)
      throws ServerCreationException;

  /**
   *
   * @param serverAddress            address of the server
   * @param schedulerSupplier        work manager source to use for retrieving an {@link Executor} for processing this server
   *                                 requests
   * @param usePersistentConnections if true, the connections will be kept open for subsequent requests
   * @param connectionIdleTimeout    the amount of milliseconds to keep open an idle connection @return the create Server handler
   * @param identifier               the id of the server
   * @param shutdownTimeout          time to wait for persistent connections to be closed when server is stopped.
   * @param readTimeout              time to wait while reading input in milliseconds
   * @throws ServerCreationException if it was not possible to create the Server. Most likely because the host and port is already
   *                                 in use.
   * @since 1.7.0, 1.6.1, 1.5.15
   */
  HttpServer createServerFor(ServerAddress serverAddress, Supplier<Scheduler> schedulerSupplier,
                             boolean usePersistentConnections, int connectionIdleTimeout, ServerIdentifier identifier,
                             Supplier<Long> shutdownTimeout, long readTimeout)
      throws ServerCreationException;

  /**
   *
   * @param tlsContextFactory
   * @param schedulerSupplier        work manager source to use for retrieving an {@link Executor} for processing this server
   *                                 requests
   * @param serverAddress            address of the server
   * @param usePersistentConnections if true, the connections will be kept open for subsequent requests
   * @param connectionIdleTimeout    the amount of milliseconds to keep open an idle connection
   * @param identifier               the id of the server
   * @param shutdownTimeout          time to wait for persistent connections to be closed when server is stopped.
   * @return the create Server handler
   * @throws ServerCreationException if it was not possible to create the Server. Most likely because the host and port is already
   *                                 in use.
   * @deprecated as of 1.7.0, 1.6.1, 1.5.15 use
   *             {@link #createSslServerFor(TlsContextFactory, Supplier, ServerAddress, boolean, int, ServerIdentifier, Supplier, long)}
   *             instead.
   */
  @Deprecated
  HttpServer createSslServerFor(TlsContextFactory tlsContextFactory, Supplier<Scheduler> schedulerSupplier,
                                ServerAddress serverAddress, boolean usePersistentConnections, int connectionIdleTimeout,
                                ServerIdentifier identifier, Supplier<Long> shutdownTimeout)
      throws ServerCreationException;

  /**
   *
   * @param tlsContextFactory
   * @param schedulerSupplier        work manager source to use for retrieving an {@link Executor} for processing this server
   *                                 requests
   * @param serverAddress            address of the server
   * @param usePersistentConnections if true, the connections will be kept open for subsequent requests
   * @param connectionIdleTimeout    the amount of milliseconds to keep open an idle connection
   * @param identifier               the id of the server
   * @param shutdownTimeout          time to wait for persistent connections to be closed when server is stopped.
   * @param readTimeout              time to wait while reading input in milliseconds
   * @return the create Server handler
   * @throws ServerCreationException if it was not possible to create the Server. Most likely because the host and port is already
   *                                 in use.
   * @since 1.7.0, 1.6.1, 1.5.15
   */
  HttpServer createSslServerFor(TlsContextFactory tlsContextFactory, Supplier<Scheduler> schedulerSupplier,
                                ServerAddress serverAddress, boolean usePersistentConnections, int connectionIdleTimeout,
                                ServerIdentifier identifier, Supplier<Long> shutdownTimeout, long readTimeout)
      throws ServerCreationException;

  /**
   *
   * @param identifier the id of the server
   * @return an {@link Optional} with the corresponding {@link HttpServer} or an empty one, if none was found
   * @throws ServerNotFoundException when the desired server was not found
   */
  HttpServer lookupServer(ServerIdentifier identifier) throws ServerNotFoundException;

  /**
   *
   * Frees all the resource hold by the server manager. The client is responsible for stopping all the server prior to call
   * dispose.
   */
  void dispose();
}
