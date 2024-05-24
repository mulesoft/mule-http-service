/*
 * Copyright 2023 Salesforce, Inc. All rights reserved.
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.service.http.impl.provider;

import static java.lang.String.format;
import static java.lang.System.getProperty;

import org.mule.runtime.api.scheduler.SchedulerService;
import org.mule.runtime.api.service.ServiceDefinition;
import org.mule.runtime.api.service.ServiceProvider;
import org.mule.runtime.http.api.HttpService;
import org.mule.service.http.impl.service.HttpServiceImplementation;
import org.mule.service.http.netty.impl.service.NettyHttpServiceImplementation;

import javax.inject.Inject;

public class HttpServiceProvider implements ServiceProvider {

  public static final String IMPLEMENTATION_PROPERTY_NAME = "mule.http.service.implementation";
  public static final String GRIZZLY_IMPLEMENTATION_NAME = "GRIZZLY";
  public static final String NETTY_IMPLEMENTATION_NAME = "NETTY";

  /**
   * Gets the configured HTTP Service implementation name. It can be "GRIZZLY" or "NETTY".
   *
   * @return the name of the configured implementation. Can be "GRIZZLY" or "NETTY".
   * @throws IllegalArgumentException if an invalid value is configured.
   */
  public static String getImplementationName() {
    String implementationName = getProperty(IMPLEMENTATION_PROPERTY_NAME, GRIZZLY_IMPLEMENTATION_NAME);
    if (NETTY_IMPLEMENTATION_NAME.equals(implementationName) || GRIZZLY_IMPLEMENTATION_NAME.equals(implementationName)) {
      return implementationName;
    } else {
      throw new IllegalArgumentException(format("Unknown HTTP Service implementation '%s'. Choose 'GRIZZLY' or 'NETTY'",
                                                implementationName));
    }
  }

  @Inject
  private SchedulerService schedulerService;

  @Override
  public ServiceDefinition getServiceDefinition() {
    return new ServiceDefinition(HttpService.class, getImplementation());
  }

  private HttpService getImplementation() {
    String implementationName = getImplementationName();
    if (NETTY_IMPLEMENTATION_NAME.equals(implementationName)) {
      return new NettyHttpServiceImplementation(schedulerService);
    } else {
      return new HttpServiceImplementation(schedulerService);
    }
  }
}
