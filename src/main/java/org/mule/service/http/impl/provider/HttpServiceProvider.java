/*
 * Copyright 2023 Salesforce, Inc. All rights reserved.
 */
package org.mule.service.http.impl.provider;

import org.mule.runtime.api.scheduler.SchedulerService;
import org.mule.runtime.api.service.ServiceDefinition;
import org.mule.runtime.api.service.ServiceProvider;
import org.mule.runtime.http.api.HttpService;
import org.mule.service.http.impl.service.HttpServiceImplementation;

import javax.inject.Inject;

public class HttpServiceProvider implements ServiceProvider {

  @Inject
  private SchedulerService schedulerService;

  @Override
  public ServiceDefinition getServiceDefinition() {
    HttpServiceImplementation service = new HttpServiceImplementation(schedulerService);
    ServiceDefinition serviceDefinition = new ServiceDefinition(HttpService.class, service);
    return serviceDefinition;
  }
}
