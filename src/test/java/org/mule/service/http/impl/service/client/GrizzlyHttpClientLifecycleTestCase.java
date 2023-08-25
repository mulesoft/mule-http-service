/*
 * Copyright 2023 Salesforce, Inc. All rights reserved.
 */
package org.mule.service.http.impl.service.client;

import static java.lang.Integer.getInteger;
import static java.lang.Runtime.getRuntime;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mule.runtime.api.scheduler.Scheduler;
import org.mule.runtime.api.scheduler.SchedulerConfig;
import org.mule.runtime.api.scheduler.SchedulerService;
import org.mule.runtime.http.api.client.HttpClient;
import org.mule.runtime.http.api.client.HttpClientConfiguration;
import org.mule.runtime.http.api.domain.message.request.HttpRequest;
import org.mule.tck.junit4.AbstractMuleTestCase;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class GrizzlyHttpClientLifecycleTestCase extends AbstractMuleTestCase {

  @Rule
  public ExpectedException expectedException = ExpectedException.none();

  private static final int DEFAULT_SELECTOR_THREAD_COUNT =
      getInteger(GrizzlyHttpClient.class.getName() + ".DEFAULT_SELECTOR_THREAD_COUNT",
                 Integer.max(getRuntime().availableProcessors(), 2));

  @Test
  public void cannotSendWhenNotStarted() throws Exception {
    HttpClient client = validateClientUsage();
    client.send(getHttpRequest());
  }

  @Test
  public void cannotSendAsyncWhenNotStarted() {
    HttpClient client = validateClientUsage();
    client.sendAsync(getHttpRequest());
  }

  @Test
  public void testSchedulerSize() {
    SchedulerService schedulerService = mock(SchedulerService.class);
    SchedulerConfig schedulerConfig = mock(SchedulerConfig.class);
    Scheduler scheduler = mock(Scheduler.class);
    when(schedulerService.customScheduler(any(), anyInt())).thenReturn(scheduler);
    when(schedulerConfig.withDirectRunCpuLightWhenTargetBusy(anyBoolean())).thenReturn(schedulerConfig);
    when(schedulerConfig.withMaxConcurrentTasks(anyInt())).thenReturn(schedulerConfig);
    when(schedulerConfig.withName(anyString())).thenReturn(schedulerConfig);

    HttpClient client = new GrizzlyHttpClient(mock(HttpClientConfiguration.class, RETURNS_DEEP_STUBS),
                                              schedulerService, schedulerConfig);
    client.start();
    verify(schedulerService, times(1)).customScheduler(any(), eq(DEFAULT_SELECTOR_THREAD_COUNT));
  }

  private HttpClient validateClientUsage() {
    HttpClient client = new GrizzlyHttpClient(mock(HttpClientConfiguration.class, RETURNS_DEEP_STUBS),
                                              mock(SchedulerService.class),
                                              mock(SchedulerConfig.class));

    expectedException.expect(IllegalStateException.class);
    expectedException.expectMessage("The client must be started before use.");
    return client;
  }

  private HttpRequest getHttpRequest() {
    return HttpRequest.builder().uri("http://localhost:8081/").build();
  }

}
