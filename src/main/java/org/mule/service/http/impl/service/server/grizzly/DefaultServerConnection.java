/*
 * Copyright 2023 Salesforce, Inc. All rights reserved.
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.service.http.impl.service.server.grizzly;

import org.mule.runtime.http.api.domain.request.ServerConnection;

import java.net.InetSocketAddress;

/**
 * Provices information on the server connection.
 */
public class DefaultServerConnection implements ServerConnection {

  private InetSocketAddress localAddress;

  public DefaultServerConnection(InetSocketAddress localAddress) {
    this.localAddress = localAddress;
  }

  @Override
  public InetSocketAddress getLocalHostAddress() {
    return localAddress;
  }
}
