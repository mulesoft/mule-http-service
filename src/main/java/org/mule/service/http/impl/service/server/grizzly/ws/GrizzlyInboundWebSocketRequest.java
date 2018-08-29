/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.service.http.impl.service.server.grizzly.ws;

import static org.glassfish.grizzly.ssl.SSLUtils.getSSLEngine;
import static org.mule.runtime.api.metadata.MediaType.ANY;
import static org.mule.runtime.api.metadata.MediaType.parse;
import static org.mule.runtime.http.api.ws.WebSocketProtocol.WS;
import static org.mule.runtime.http.api.ws.WebSocketProtocol.WSS;
import static org.slf4j.LoggerFactory.getLogger;
import org.mule.runtime.api.metadata.MediaType;
import org.mule.runtime.http.api.domain.entity.EmptyHttpEntity;
import org.mule.runtime.http.api.domain.entity.HttpEntity;
import org.mule.runtime.http.api.domain.request.ClientConnection;
import org.mule.runtime.http.api.domain.request.ServerConnection;
import org.mule.runtime.http.api.ws.WebSocketProtocol;
import org.mule.runtime.http.api.ws.WebSocketRequest;
import org.mule.service.http.impl.service.server.grizzly.DefaultServerConnection;
import org.mule.service.http.impl.service.server.grizzly.GrizzlyHttpMessage;
import org.mule.service.http.impl.util.HttpUtils;

import java.net.InetSocketAddress;
import java.net.URI;

import org.glassfish.grizzly.http.HttpRequestPacket;
import org.slf4j.Logger;

public class GrizzlyInboundWebSocketRequest extends GrizzlyHttpMessage implements WebSocketRequest {

  private static final Logger LOGGER = getLogger(GrizzlyInboundWebSocketRequest.class);

  private final HttpEntity entity = new EmptyHttpEntity();
  private WebSocketProtocol scheme;
  private ServerConnection serverConnection;
  private ClientConnection clientConnection;
  private MediaType contentType;
  private String httpVersion;

  public GrizzlyInboundWebSocketRequest(HttpRequestPacket requestPacket) {
    super(requestPacket, null, (InetSocketAddress) requestPacket.getConnection().getLocalAddress());
    httpVersion = requestPacket.getProtocol().toString();
  }

  @Override
  public WebSocketProtocol getScheme() {
    if (scheme == null) {
      scheme = getSSLEngine(requestPacket.getConnection()) != null || getClientConnection().getClientCertificate() != null
          ? WSS
          : WS;
    }
    return scheme;
  }

  @Override
  public String getHttpVersion() {
    if (httpVersion == null) {
      httpVersion = requestPacket.getProtocol().toString();
    }

    return httpVersion;
  }

  @Override
  public URI getRequestUri() {
    return getUri();
  }

  @Override
  public MediaType getContentType() {
    if (contentType == null) {
      try {
        final String contentType = requestPacket.getContentType();
        this.contentType = contentType != null ? parse(contentType) : ANY;
      } catch (Exception e) {
        if (LOGGER.isInfoEnabled()) {
          LOGGER.info("Received inbound WebSocket request at path {} with invalid Content-Type '{}'. Will default to '{}'",
                      getPath(), contentType, ANY.toRfcString());
        }
        contentType = ANY;
      }
    }

    return contentType;
  }

  @Override
  public ServerConnection getServerConnection() {
    if (serverConnection == null) {
      serverConnection = new DefaultServerConnection((InetSocketAddress) requestPacket.getConnection().getLocalAddress());
    }

    return serverConnection;
  }

  @Override
  public ClientConnection getClientConnection() {
    if (clientConnection == null) {
      clientConnection = HttpUtils.getClientConnection(requestPacket.getConnection());
    }
    return clientConnection;
  }

  @Override
  protected String getBaseProtocol() {
    return getScheme().getScheme();
  }

  @Override
  public HttpEntity getEntity() {
    return entity;
  }
}
