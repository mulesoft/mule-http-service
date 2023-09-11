/*
 * Copyright 2023 Salesforce, Inc. All rights reserved.
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.service.http.impl.service.client;

import static java.lang.Integer.parseInt;
import static org.glassfish.grizzly.http.util.MimeHeaders.MAX_NUM_HEADERS_DEFAULT;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mule.runtime.api.util.MuleSystemProperties.SYSTEM_PROPERTY_PREFIX;
import static org.mule.tck.junit4.rule.SystemProperty.callWithProperty;

import org.mule.runtime.api.scheduler.Scheduler;
import org.mule.runtime.api.scheduler.SchedulerConfig;
import org.mule.runtime.api.scheduler.SchedulerService;
import org.mule.runtime.http.api.client.HttpClient;
import org.mule.runtime.http.api.client.HttpClientConfiguration;
import org.mule.service.http.impl.service.server.grizzly.GrizzlyServerManager;
import org.mule.tck.junit4.AbstractMuleTestCase;

import java.lang.reflect.Field;

import com.ning.http.client.AsyncHttpClient;
import io.qameta.allure.Description;
import io.qameta.allure.Issue;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class GrizzlyHttpClientTestCase extends AbstractMuleTestCase {

  SchedulerService schedulerService;
  SchedulerConfig schedulerConfig;
  SchedulerConfig schedulerConfig2;

  @Before
  public void setUp() {
    schedulerService = mock(SchedulerService.class);
    schedulerConfig = mock(SchedulerConfig.class);
    schedulerConfig2 = mock(SchedulerConfig.class);

    when(schedulerService.customScheduler(any())).thenReturn(mock(Scheduler.class));
    when(schedulerService.ioScheduler(any())).thenReturn(mock(Scheduler.class));
    when(schedulerConfig.withDirectRunCpuLightWhenTargetBusy(anyBoolean())).thenReturn(schedulerConfig2);
    when(schedulerConfig2.withMaxConcurrentTasks(anyInt())).thenReturn(mock(SchedulerConfig.class));
    when(schedulerConfig.withName(any())).thenReturn(mock(SchedulerConfig.class));
  }

  @After
  public void tearDown() {
    GrizzlyServerManager.refreshSystemProperties();
  }

  @Issue("MULE-19837")
  @Description("When the max number of request headers are NOT set by System Properties, they should be " +
      "assigned correctly by default. We check that the variables are properly set because we are delegating the max headers" +
      " amount check to Grizzly")
  @Test
  public void testMaxClientRequestHeadersIfNotSetBySystemPropertyIsSetByDefault() throws Throwable {
    HttpClient client = refreshSystemPropertiesAndCreateGrizzlyHttpClient();

    client.start();

    Field asyncHttpClientField = GrizzlyHttpClient.class.getDeclaredField("asyncHttpClient");
    asyncHttpClientField.setAccessible(true);
    AsyncHttpClient asyncHttpClient =
        (AsyncHttpClient) asyncHttpClientField.get(client);

    assertThat(asyncHttpClient.getConfig().getMaxRequestHeaders(), is(MAX_NUM_HEADERS_DEFAULT));
  }

  @Issue("MULE-19837")
  @Description("When the max number of request headers are set by System Properties, they should be " +
      "assigned correctly to Grizzly's AsyncHttpClient. We check that the variables are properly set because we are delegating the max headers"
      + " amount check to Grizzly")
  @Test
  public void testMaxClientRequestHeadersCanBeSetBySystemProperty() throws Throwable {
    String maxSetRequestHeaders = "150";
    HttpClient client = callWithProperty(SYSTEM_PROPERTY_PREFIX + "http.MAX_CLIENT_REQUEST_HEADERS", maxSetRequestHeaders,
                                         this::refreshSystemPropertiesAndCreateGrizzlyHttpClient);

    client.start();

    Field asyncHttpClientField = GrizzlyHttpClient.class.getDeclaredField("asyncHttpClient");
    asyncHttpClientField.setAccessible(true);
    AsyncHttpClient asyncHttpClient =
        (AsyncHttpClient) asyncHttpClientField.get(client);

    assertThat(asyncHttpClient.getConfig().getMaxRequestHeaders(), is(parseInt(maxSetRequestHeaders)));
  }

  private GrizzlyHttpClient refreshSystemPropertiesAndCreateGrizzlyHttpClient() {
    GrizzlyHttpClient.refreshSystemProperties();
    return new GrizzlyHttpClient(mock(HttpClientConfiguration.class, RETURNS_DEEP_STUBS),
                                 schedulerService,
                                 schedulerConfig);
  }
}
