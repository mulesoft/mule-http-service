/*
 * Copyright 2023 Salesforce, Inc. All rights reserved.
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.service.http.impl.util;

import static org.mule.service.http.impl.service.server.grizzly.MuleSslFilter.SSL_SESSION_ATTRIBUTE_KEY;
import org.mule.runtime.http.api.domain.request.ClientConnection;
import org.mule.service.http.impl.service.server.grizzly.DefaultClientConnection;

import java.net.InetSocketAddress;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLSession;

import org.glassfish.grizzly.Connection;
import org.glassfish.grizzly.ssl.SSLUtils;

public final class HttpUtils {

  public static final String WILDCARD_CHARACTER = "*";
  public static final String SLASH = "/";

  public static ClientConnection getClientConnection(Connection connection) {
    SSLSession sslSession = (SSLSession) connection.getAttributes().getAttribute(SSL_SESSION_ATTRIBUTE_KEY);
    if (sslSession == null) {
      SSLEngine engine = SSLUtils.getSSLEngine(connection);
      if (engine != null) {
        sslSession = engine.getSession();
      }
    }

    if (sslSession != null) {
      return new DefaultClientConnection(sslSession, ((InetSocketAddress) connection.getPeerAddress()));
    } else {
      return new DefaultClientConnection((InetSocketAddress) connection.getPeerAddress());
    }
  }

  private HttpUtils() {}
}
