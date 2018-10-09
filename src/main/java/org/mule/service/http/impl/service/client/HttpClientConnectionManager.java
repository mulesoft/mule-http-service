/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.service.http.impl.service.client;

import org.mule.runtime.api.scheduler.SchedulerConfig;
import org.mule.runtime.api.scheduler.SchedulerService;
import org.mule.runtime.http.api.client.ClientNotFoundException;
import org.mule.runtime.http.api.client.HttpClient;
import org.mule.runtime.http.api.client.HttpClientConfiguration;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Manages client connections
 *
 * @since 1.1.5
 */
public class HttpClientConnectionManager implements ContextHttpClientFactory {

  private final ConcurrentMap<ClientIdentifier, HttpClient> clients = new ConcurrentHashMap<>();

  @Override
  public HttpClient create(HttpClientConfiguration config,
                           String context,
                           SchedulerService schedulerService,
                           SchedulerConfig schedulerConfig) {

    ClientIdentifier identifier = new ClientIdentifier(context, config.getName());
    if (clients.containsKey(identifier)) {
      throw new IllegalArgumentException(String.format("Http client of name '%s' already exists for artifact '%s'",
                                                       identifier.getName(), identifier.getContext()));
    }

    HttpClient client = doCreateClient(config, schedulerService, schedulerConfig);
    clients.put(identifier, client);

    return client;
  }

  protected HttpClient doCreateClient(HttpClientConfiguration config,
                                      SchedulerService schedulerService,
                                      SchedulerConfig schedulerConfig) {
    return new GrizzlyHttpClient(config, schedulerService, schedulerConfig);
  }

  public HttpClient lookupClient(ClientIdentifier identifier) throws ClientNotFoundException {
    HttpClient client = clients.get(identifier);
    if (client == null) {
      throw new ClientNotFoundException(identifier.getName());
    }

    return client;
  }
}
