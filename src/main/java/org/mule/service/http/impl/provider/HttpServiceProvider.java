/*
 * Copyright 2023 Salesforce, Inc. All rights reserved.
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.service.http.impl.provider;

import static java.lang.System.getProperty;

import org.mule.runtime.api.scheduler.SchedulerService;
import org.mule.runtime.api.service.ServiceDefinition;
import org.mule.runtime.api.service.ServiceProvider;
import org.mule.runtime.http.api.HttpService;
import org.mule.service.http.impl.service.HttpServiceImplementation;
import org.mule.service.http.netty.impl.service.NettyHttpServiceImplementation;

import javax.inject.Inject;

public class HttpServiceProvider implements ServiceProvider {

  // TODO: Change default to GRIZZLY
  private static final String IMPLEMENTATION_NAME = getProperty("mule.http.service.implementation", "NETTY");

  @Inject
  private SchedulerService schedulerService;

  @Override
  public ServiceDefinition getServiceDefinition() {
    return new ServiceDefinition(HttpService.class, getImpl());
  }

  private HttpService getImpl() {
    if ("NETTY".equals(IMPLEMENTATION_NAME)) {
      return new NettyHttpServiceImplementation(schedulerService);
    } else if ("GRIZZLY".equals(IMPLEMENTATION_NAME)) {
      return new HttpServiceImplementation(schedulerService);
    } else {
      throw new IllegalArgumentException(String
          .format("Unknown HTTP Service implementation '%s'. Choose 'GRIZZLY' or 'NETTY'", IMPLEMENTATION_NAME));
    }
  }
}
