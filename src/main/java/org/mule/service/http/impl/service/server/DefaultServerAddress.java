/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.service.http.impl.service.server;

import static org.mule.runtime.http.api.HttpConstants.ALL_INTERFACES_ADDRESS;

import org.mule.runtime.http.api.server.ServerAddress;

import java.net.InetAddress;

public class DefaultServerAddress implements ServerAddress {

  private final InetAddress address;
  private int port;

  public DefaultServerAddress(InetAddress address, int port) {
    this.port = port;
    this.address = address;
  }

  @Override
  public int getPort() {
    return port;
  }

  @Override
  public String getIp() {
    return address.getHostAddress();
  }

  @Override
  public InetAddress getAddress() {
    return address;
  }

  @Override
  public boolean overlaps(ServerAddress serverAddress) {
    return (port == serverAddress.getPort()) && (isAllInterfaces(this) || isAllInterfaces(serverAddress));
  }

  public static boolean isAllInterfaces(ServerAddress serverAddress) {
    return ALL_INTERFACES_ADDRESS.equals(serverAddress.getAddress());
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    ServerAddress that = (ServerAddress) o;

    if (port != that.getPort()) {
      return false;
    }
    if (!address.equals(that.getAddress())) {
      return false;
    }

    return true;
  }

  @Override
  public int hashCode() {
    int result = address.hashCode();
    result = 31 * result + port;
    return result;
  }

  @Override
  public String toString() {
    return "ServerAddress{" + "ip='" + address.getHostAddress() + '\'' + ", port=" + port + '}';
  }
}
