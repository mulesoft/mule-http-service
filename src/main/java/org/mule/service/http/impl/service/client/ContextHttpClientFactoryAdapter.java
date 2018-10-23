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
import org.mule.runtime.http.api.client.HttpClientFactory;

/**
 * Adapts a {@link ContextHttpClientFactory} to a {@link HttpClientFactory}.
 *
 * @since 1.1.5
 */
public class ContextHttpClientFactoryAdapter implements HttpClientFactory {

  private final String context;
  private final SchedulerService schedulerService;
  private final SchedulerConfig schedulerConfig;
  private final ContextHttpClientFactory delegate;

  public ContextHttpClientFactoryAdapter(String context,
                                         SchedulerService schedulerService,
                                         SchedulerConfig schedulerConfig,
                                         ContextHttpClientFactory delegate) {
    this.context = context;
    this.schedulerService = schedulerService;
    this.schedulerConfig = schedulerConfig;
    this.delegate = delegate;
  }

  @Override
  public HttpClient create(HttpClientConfiguration configuration) {
    return delegate.create(configuration, context, schedulerService, schedulerConfig);
  }

  @Override
  public HttpClient lookup(String name) throws ClientNotFoundException {
    return delegate.lookupClient(new ClientIdentifier(context, name));
  }
}
