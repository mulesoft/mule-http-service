/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.service.http.impl.service.server;

import org.mule.runtime.http.api.server.HttpServer;
import org.mule.runtime.http.api.server.HttpServerConfiguration;
import org.mule.runtime.http.api.server.ServerCreationException;
import org.mule.runtime.http.api.server.ServerNotFoundException;

import java.util.Optional;
import java.util.function.Supplier;

/**
 * Factory object for {@link HttpServer} that partitions them considering a given creation context in which they can be later
 * shared.
 *
 * @since 1.0
 */
public interface ContextHttpServerFactory {

  /**
   * Creates a new {@link HttpServer}.
   *
   * @param configuration   a {@link HttpServerConfiguration} specifying the desired server.
   * @param context         the context under which this server will be created
   * @param shutdownTimeout Time to wait for persistent connections to be closed when server is stopped.
   * @return a newly built {@link HttpServer} based on the {@code configuration}.
   * @throws ServerCreationException if the server cannot be created based on the configuration.
   */
  HttpServer create(HttpServerConfiguration configuration, String context, Supplier<Long> shutdownTimeout)
      throws ServerCreationException;

  /**
   * Allows to retrieve a previously created {@link HttpServer}. This will only be possible if used from the same context.
   *
   * @param identifier the identifier of the server
   * @return an {@link Optional} with the server or an empty one, if none was found.
   * @throws ServerNotFoundException when the desired server was not found
   */
  HttpServer lookup(ServerIdentifier identifier) throws ServerNotFoundException;

}
