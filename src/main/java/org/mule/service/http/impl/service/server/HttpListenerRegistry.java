/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.service.http.impl.service.server;

import static org.mule.runtime.core.api.util.concurrent.FunctionalReadWriteLock.readWriteLock;
import static org.slf4j.LoggerFactory.getLogger;
import org.mule.runtime.core.api.util.concurrent.FunctionalReadWriteLock;
import org.mule.runtime.http.api.domain.message.request.HttpRequest;
import org.mule.runtime.http.api.server.HttpServer;
import org.mule.runtime.http.api.server.PathAndMethodRequestMatcher;
import org.mule.runtime.http.api.server.RequestHandler;
import org.mule.runtime.http.api.server.RequestHandlerManager;
import org.mule.runtime.http.api.server.ServerAddress;
import org.mule.runtime.http.api.utils.RequestMatcherRegistry;
import org.mule.service.http.impl.service.util.DefaultRequestMatcherRegistryBuilder;

import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;

/**
 * Registry of servers and its handlers, which allows searching for handlers and introducing new ones (while allowing them to be
 * managed).
 */
public class HttpListenerRegistry implements RequestHandlerProvider {

  private static final Logger LOGGER = getLogger(HttpListenerRegistry.class);

  private final ServerAddressMap<HttpServer> serverAddressToServerMap = new ServerAddressMap<>();
  private final Map<HttpServer, RequestMatcherRegistry<RequestHandler>> requestHandlerPerServerAddress = new HashMap<>();
  private final FunctionalReadWriteLock lock = readWriteLock();

  /**
   * Introduces a new {@link RequestHandler} for requests matching a given {@link PathAndMethodRequestMatcher} in the provided
   * {@link HttpServer}.
   *
   * @param server where the handler should be added
   * @param requestHandler the handler to add
   * @param requestMatcher the matcher to be applied for the handler
   * @return a {@link RequestHandlerManager} for the added handler that allows enabling, disabling and disposing it
   */
  public RequestHandlerManager addRequestHandler(final HttpServer server, final RequestHandler requestHandler,
                                                 final PathAndMethodRequestMatcher requestMatcher) {
    return lock.withWriteLock(() -> {
      RequestMatcherRegistry<RequestHandler> serverAddressRequestHandlerRegistry =
          this.requestHandlerPerServerAddress.get(server);
      if (serverAddressRequestHandlerRegistry == null) {
        serverAddressRequestHandlerRegistry = new DefaultRequestMatcherRegistryBuilder<RequestHandler>()
            .onMethodMismatch(NoMethodRequestHandler::getInstance)
            .onNotFound(NoListenerRequestHandler::getInstance)
            .onInvalidRequest(BadRequestHandler::getInstance)
            .onDisabled(ServiceTemporarilyUnavailableListenerRequestHandler::getInstance)
            .build();
        requestHandlerPerServerAddress.put(server, serverAddressRequestHandlerRegistry);
        serverAddressToServerMap.put(server.getServerAddress(), server);
      }
      return new DefaultRequestHandlerManager(serverAddressRequestHandlerRegistry.add(requestMatcher, requestHandler));
    });
  }

  /**
   * Removes all handlers for a given {@link HttpServer}.
   *
   * @param server whose handlers will be removed
   */
  public void removeHandlersFor(HttpServer server) {
    lock.withWriteLock(() -> {
      requestHandlerPerServerAddress.remove(server);
      serverAddressToServerMap.remove(server.getServerAddress());
    });
  }

  @Override
  public boolean hasHandlerFor(ServerAddress serverAddress) {
    return lock.withReadLock(r -> serverAddressToServerMap.get(serverAddress) != null);
  }

  @Override
  public RequestHandler getRequestHandler(ServerAddress serverAddress, final HttpRequest request) {
    LOGGER.debug("Looking RequestHandler for request: {}", request.getPath());
    return lock.withReadLock(r -> {
      final HttpServer server = serverAddressToServerMap.get(serverAddress);
      if (server != null && !server.isStopped()) {
        final RequestMatcherRegistry<RequestHandler> serverAddressRequestHandlerRegistry =
            requestHandlerPerServerAddress.get(server);
        if (serverAddressRequestHandlerRegistry != null) {
          return serverAddressRequestHandlerRegistry.find(request);
        }
      }
      LOGGER.debug("No RequestHandler found for request: {}", request.getPath());
      return NoListenerRequestHandler.getInstance();
    });
  }

}
