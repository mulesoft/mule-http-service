/*
 * Copyright 2023 Salesforce, Inc. All rights reserved.
 */
package org.mule.service.http.impl.service.server;

/**
 * Allows to identify a server by it's creation context and name.
 *
 * @since 1.0
 */
public class ServerIdentifier {

  private final String context;
  private final String name;

  public ServerIdentifier(String context, String name) {
    this.context = context;
    this.name = name;
  }

  public String getName() {
    return name;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    ServerIdentifier that = (ServerIdentifier) o;

    if (!context.equals(that.context)) {
      return false;
    }
    return name.equals(that.name);
  }

  @Override
  public int hashCode() {
    int result = context.hashCode();
    result = 31 * result + name.hashCode();
    return result;
  }
}
