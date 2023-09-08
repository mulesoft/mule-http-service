/*
 * Copyright 2023 Salesforce, Inc. All rights reserved.
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.service.http.impl.service.client;

import org.mule.runtime.api.scheduler.SchedulerConfig;
import org.mule.runtime.api.scheduler.SchedulerService;
import org.mule.runtime.http.api.client.HttpClient;
import org.mule.runtime.http.api.client.HttpClientConfiguration;

/**
 * Manages client connections.
 *
 * @since 1.3.0
 */
public class HttpClientConnectionManager {

  protected final SchedulerService schedulerService;

  public HttpClientConnectionManager(SchedulerService schedulerService) {
    this.schedulerService = schedulerService;
  }

  public HttpClient create(HttpClientConfiguration config, SchedulerConfig schedulerConfig) {
    return new GrizzlyHttpClient(config, schedulerService, schedulerConfig);
  }

}
