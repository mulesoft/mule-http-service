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

/**
 * Factory object for {@link HttpClient} that partitions them considering a given creation context in which they can be later
 * shared.
 *
 * @since 1.1.5
 */
public interface ContextHttpClientFactory {

  /**
   * Creates a new {@link HttpClient}
   *
   * @param config           the {@link HttpClientConfiguration}
   * @param context          the context under which this server will be created
   * @param schedulerService the {@link SchedulerService} to be used for async tasks
   * @param schedulerConfig  a {@link SchedulerConfig}
   * @return a new {@link HttpClient}
   */
  HttpClient create(HttpClientConfiguration config, String context, SchedulerService schedulerService,
                    SchedulerConfig schedulerConfig);

  /**
   * Retrieves the {@link HttpClient} for the given {@code clientIdentifier}
   *
   * @param clientIdentifier an identified
   * @return an {@link HttpClient} previously created through {@link #create(HttpClientConfiguration, String, SchedulerService, SchedulerConfig)}
   * @throws ClientNotFoundException if no such client exists
   */
  HttpClient lookupClient(ClientIdentifier clientIdentifier) throws ClientNotFoundException;
}
