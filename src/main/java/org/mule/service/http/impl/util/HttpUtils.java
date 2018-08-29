/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.service.http.impl.util;

import static org.mule.service.http.impl.service.server.grizzly.MuleSslFilter.SSL_SESSION_ATTRIBUTE_KEY;
import org.mule.runtime.core.api.util.StringUtils;
import org.mule.runtime.http.api.domain.request.ClientConnection;
import org.mule.runtime.http.api.server.ServerAddress;
import org.mule.service.http.impl.service.server.DefaultServerAddress;
import org.mule.service.http.impl.service.server.grizzly.DefaultClientConnection;

import java.net.InetSocketAddress;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLSession;

import org.glassfish.grizzly.Connection;
import org.glassfish.grizzly.ssl.SSLUtils;

public final class HttpUtils {

  public static final String WILDCARD_CHARACTER = "*";
  public static final String SLASH = "/";

  public static ServerAddress toServerAddress(Connection connection) {
    final InetSocketAddress inetAddress = (InetSocketAddress) connection.getLocalAddress();
    final int port = inetAddress.getPort();
    final String ip = inetAddress.getAddress().getHostAddress();
    return new DefaultServerAddress(ip, port);
  }

  public static boolean isCatchAllPath(String path) {
    return WILDCARD_CHARACTER.equals(path);
  }

  public static boolean isUriParameter(String pathPart) {
    return (pathPart.startsWith("{") || pathPart.startsWith("/{")) && pathPart.endsWith("}");
  }

  public static boolean isSameDepth(String possibleCollisionRequestMatcherPath, String newListenerRequestMatcherPath) {
    return getPathPartsSize(possibleCollisionRequestMatcherPath) == getPathPartsSize(newListenerRequestMatcherPath);
  }

  public static int getPathPartsSize(String path) {
    int pathSize = splitPath(path).length - 1;
    pathSize += (path.endsWith(SLASH) ? 1 : 0);
    return pathSize;
  }

  public static String[] splitPath(String path) {
    if (path.endsWith(SLASH)) {
      // Remove the last slash
      path = path.substring(0, path.length() - 1);
    }
    return path.split(SLASH, -1);
  }

  public static String getLastPathPortion(String possibleCollisionRequestMatcherPath) {
    final String[] parts = splitPath(possibleCollisionRequestMatcherPath);
    if (parts.length == 0) {
      return StringUtils.EMPTY;
    }
    return parts[parts.length - 1];
  }

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
