/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.service.http.impl.service.server;

import org.mule.runtime.http.api.domain.message.request.HttpRequest;
import org.mule.runtime.http.api.server.RequestHandler;
import org.mule.runtime.http.api.server.ServerAddress;

/**
 * Provider of {@link RequestHandler} for a certain incoming http request.
 *
 * @since 1.0
 */
public interface RequestHandlerProvider {

  /**
   * Retrieves a RequestHandler to handle the http request
   *
   * @param serverAddress address in which the http request was made
   * @param request the http request content
   * @return a handler for the request
   */
  RequestHandler getRequestHandler(ServerAddress serverAddress, HttpRequest request);

  /**
   * Checks if a handler for a specific {@link ServerAddress} is present
   *
   * @param serverAddress the address to check for
   * @return {@code true} if there is a handler present for the address, {@code false} otherwise
   */
  boolean hasHandlerFor(ServerAddress serverAddress);

}
