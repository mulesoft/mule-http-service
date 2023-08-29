/*
 * Copyright 2023 Salesforce, Inc. All rights reserved.
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.service.http.impl.service.server.grizzly;

import org.mule.runtime.http.api.domain.message.request.HttpRequest;
import org.mule.runtime.http.api.domain.request.ClientConnection;
import org.mule.runtime.http.api.domain.request.HttpRequestContext;
import org.mule.runtime.http.api.domain.request.ServerConnection;

/**
 * Holds the input from an http request.
 */
public class DefaultHttpRequestContext implements HttpRequestContext {

  private final ClientConnection clientConnection;
  private final ServerConnection serverConnection;
  private HttpRequest request;
  private String scheme;

  public DefaultHttpRequestContext(String scheme, HttpRequest httpRequest, ClientConnection clientConnection,
                                   ServerConnection serverConnection) {
    this.request = httpRequest;
    this.clientConnection = clientConnection;
    this.scheme = scheme;
    this.serverConnection = serverConnection;
  }

  @Override
  public ServerConnection getServerConnection() {
    return serverConnection;
  }

  /**
   * @return the http request content
   */
  public HttpRequest getRequest() {
    return this.request;
  }

  /**
   * @return client connection descriptor
   */
  public ClientConnection getClientConnection() {
    return clientConnection;
  }

  /**
   * @return The scheme of the HTTP request URL (http or https)
   */
  public String getScheme() {
    return scheme;
  }
}
