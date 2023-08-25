/*
 * Copyright 2023 Salesforce, Inc. All rights reserved.
 */
package org.mule.service.http.impl.service.server;

import org.mule.runtime.http.api.server.RequestHandlerManager;
import org.mule.runtime.http.api.utils.RequestMatcherRegistry;

public class DefaultRequestHandlerManager implements RequestHandlerManager {

  private final RequestMatcherRegistry.RequestMatcherRegistryEntry entry;

  public DefaultRequestHandlerManager(RequestMatcherRegistry.RequestMatcherRegistryEntry entry) {
    this.entry = entry;
  }

  @Override
  public void stop() {
    entry.disable();
  }

  @Override
  public void start() {
    entry.enable();
  }

  @Override
  public void dispose() {
    entry.remove();
  }
}
