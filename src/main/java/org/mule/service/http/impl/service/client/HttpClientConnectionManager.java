/*
 * Copyright 2023 Salesforce, Inc. All rights reserved.
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
