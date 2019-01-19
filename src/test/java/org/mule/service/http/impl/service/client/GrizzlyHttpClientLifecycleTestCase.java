/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.service.http.impl.service.client;

import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
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
