/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */

package org.mule.service.http.impl.service.server;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.mule.runtime.api.scheduler.Scheduler;
import org.mule.runtime.api.scheduler.SchedulerConfig;
import org.mule.runtime.api.scheduler.SchedulerService;
import org.mule.runtime.http.api.server.HttpServerConfiguration;
import org.mule.service.http.impl.service.server.grizzly.GrizzlyServerManager;

import io.qameta.allure.Description;
import io.qameta.allure.Issue;
import org.junit.Test;

public class HttpListenerConnectionManagerTestCase {

  private GrizzlyServerManager grizzlyServerManager;

  @Test
  @Issue("MULE-19779")
  @Description("Tests that, when specified, the read timeout is set to a custom value")
  public void setCustomReadTimeoutTo20secs() throws Exception {
    SchedulerService schedulerServiceMock = mock(SchedulerService.class);
    SchedulerConfig schedulerConfigMock = mock(SchedulerConfig.class);
    Scheduler scheduler = mock(Scheduler.class);
    long readTimeout = 20000L;
    HttpServerConfiguration serverConfiguration = new HttpServerConfiguration.Builder()
        .setName("CONFIG_NAME")
        .setHost("localhost")
        .setPort(8081)
        .setReadTimeout(readTimeout)
        .build();
    long shutdownTimeout = 50L;

    when(schedulerServiceMock.customScheduler(any())).thenReturn(scheduler);
    when(schedulerServiceMock.ioScheduler(any())).thenReturn(scheduler);
    when(schedulerConfigMock.withMaxConcurrentTasks(any(int.class))).thenReturn(schedulerConfigMock);
    when(schedulerConfigMock.withName(any())).thenReturn(schedulerConfigMock);

    HttpListenerConnectionManager httpListenerConnectionManager =
        new TestHttpListenerConnectionManager(schedulerServiceMock, schedulerConfigMock);
    httpListenerConnectionManager.initialise();
    httpListenerConnectionManager.create(serverConfiguration, "context", () -> shutdownTimeout);

    verify(grizzlyServerManager).createServerFor(any(), any(), any(boolean.class), any(int.class), any(), any(), eq(readTimeout));
  }

  @Test
  @Issue("MULE-19779")
  @Description("Tests that by default the read timeout is set to 30 seconds")
  public void setDefaultReadTimeoutTo30secs() throws Exception {
    SchedulerService schedulerServiceMock = mock(SchedulerService.class);
    SchedulerConfig schedulerConfigMock = mock(SchedulerConfig.class);
    Scheduler scheduler = mock(Scheduler.class);
    long readTimeout = 30000L;
    HttpServerConfiguration serverConfiguration = new HttpServerConfiguration.Builder()
        .setName("CONFIG_NAME")
        .setHost("localhost")
        .setPort(8081)
        .build();
    long shutdownTimeout = 50L;

    when(schedulerServiceMock.customScheduler(any())).thenReturn(scheduler);
    when(schedulerServiceMock.ioScheduler(any())).thenReturn(scheduler);
    when(schedulerConfigMock.withMaxConcurrentTasks(any(int.class))).thenReturn(schedulerConfigMock);
    when(schedulerConfigMock.withName(any())).thenReturn(schedulerConfigMock);

    HttpListenerConnectionManager httpListenerConnectionManager =
        new TestHttpListenerConnectionManager(schedulerServiceMock, schedulerConfigMock);
    httpListenerConnectionManager.initialise();
    httpListenerConnectionManager.create(serverConfiguration, "context", () -> shutdownTimeout);

    verify(grizzlyServerManager).createServerFor(any(), any(), any(boolean.class), any(int.class), any(), any(), eq(readTimeout));
  }

  class TestHttpListenerConnectionManager extends HttpListenerConnectionManager {

    public TestHttpListenerConnectionManager(SchedulerService schedulerService, SchedulerConfig schedulersConfig) {
      super(schedulerService, schedulersConfig);
    }

    @Override
    public GrizzlyServerManager createServerManager() {
      grizzlyServerManager = mock(GrizzlyServerManager.class);
      return grizzlyServerManager;
    }
  }
}
