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

import java.util.Optional;

/**
 * Adapts a {@link ContextHttpServerFactory} to a {@link HttpServerFactory}.
 *
 * @since 1.0
 */
public class ContextHttpServerFactoryAdapter implements HttpServerFactory {

  public static final boolean USE_IO_SCHEDULER = true;

  private final Optional<String> parentContext;
  private final String context;
  private final ContextHttpServerFactory delegate;

  public ContextHttpServerFactoryAdapter(String context, Optional<String> parentContext, ContextHttpServerFactory delegate) {
    this.context = context;
    this.parentContext = parentContext;
    this.delegate = delegate;
  }

  @Override
  public HttpServer create(HttpServerConfiguration configuration) throws ServerCreationException {
    return delegate.create(configuration, context);
  }

  @Override
  public HttpServer lookup(String name) throws ServerNotFoundException {
    try {
      return delegate.lookup(new ServerIdentifier(context, name));
    } catch (ServerNotFoundException e) {
      if (parentContext.isPresent()) {
        return delegate.lookup(new ServerIdentifier(parentContext.get(), name));
      }
      throw e;
    }
  }
}
