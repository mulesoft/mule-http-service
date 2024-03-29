/*
 * Copyright 2023 Salesforce, Inc. All rights reserved.
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
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
