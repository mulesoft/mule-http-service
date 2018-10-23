/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.service.http.impl.service.client;

import java.util.Objects;

/**
 * Allows to identify a client by it's creation context and name.
 *
 * @since 1.1.5
 */
public class ClientIdentifier {

  private final String context;
  private final String name;

  public ClientIdentifier(String context, String name) {
    this.context = context;
    this.name = name;
  }

  public String getName() {
    return name;
  }

  public String getContext() {
    return context;
  }

  @Override
  public boolean equals(Object o) {
    if (o instanceof ClientIdentifier) {
      ClientIdentifier that = (ClientIdentifier) o;
      return Objects.equals(context, that.context) && Objects.equals(name, that.name);
    }
    return false;
  }

  @Override
  public int hashCode() {
    int result = context.hashCode();
    result = 31 * result + name.hashCode();
    return result;
  }
}
