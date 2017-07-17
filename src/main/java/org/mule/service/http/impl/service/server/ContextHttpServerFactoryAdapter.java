/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.service.http.impl.service.server;

import org.mule.runtime.http.api.server.HttpServer;
import org.mule.runtime.http.api.server.HttpServerConfiguration;
import org.mule.runtime.http.api.server.HttpServerFactory;
import org.mule.runtime.http.api.server.ServerCreationException;
import org.mule.runtime.http.api.server.ServerNotFoundException;

/**
 * Adapts a {@link ContextHttpServerFactory} to a {@link HttpServerFactory}.
 *
 * @since 1.0
 */
public class ContextHttpServerFactoryAdapter implements HttpServerFactory {

  private final String context;
  private final ContextHttpServerFactory delegate;

  public ContextHttpServerFactoryAdapter(String context, ContextHttpServerFactory delegate) {
    this.context = context;
    this.delegate = delegate;
  }

  @Override
  public HttpServer create(HttpServerConfiguration configuration) throws ServerCreationException {
    return delegate.create(configuration, context);
  }

  @Override
  public HttpServer lookup(String name) throws ServerNotFoundException {
    return delegate.lookup(new ServerIdentifier(context, name));
  }
}
