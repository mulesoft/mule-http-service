/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
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
