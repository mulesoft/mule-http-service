/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.service.http.impl.service.client;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.junit.Assert.assertThat;
import static org.junit.rules.ExpectedException.none;
import static org.mockito.Mockito.mock;
import org.mule.runtime.api.scheduler.SchedulerConfig;
import org.mule.runtime.api.scheduler.SchedulerService;
import org.mule.runtime.http.api.client.ClientNotFoundException;
import org.mule.runtime.http.api.client.HttpClient;
import org.mule.runtime.http.api.client.HttpClientConfiguration;
import org.mule.tck.junit4.AbstractMuleTestCase;
import org.mule.tck.size.SmallTest;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

@SmallTest
public class HttpClientConnectionManagerTestCase extends AbstractMuleTestCase {

  private static final String NAME = "da client";
  private static final String CONTEXT = "mock";

  @Rule
  public ExpectedException expectedException = none();
  private HttpClientConnectionManager connectionManager = new HttpClientConnectionManager();

  @Test
  public void createAndRecover() throws Exception {
    HttpClient client = connectionManager.create(new HttpClientConfiguration.Builder().setName(NAME).build(),
                                                 CONTEXT,
                                                 mock(SchedulerService.class),
                                                 mock(SchedulerConfig.class));

    assertThat(client, is(not(nullValue())));
    assertThat(connectionManager.lookupClient(new ClientIdentifier(CONTEXT, NAME)), is(sameInstance(client)));
  }

  @Test
  public void lookupUnexistingClientInCorrectContext() throws Exception {
    HttpClient client = connectionManager.create(new HttpClientConfiguration.Builder().setName(NAME).build(),
                                                 CONTEXT,
                                                 mock(SchedulerService.class),
                                                 mock(SchedulerConfig.class));

    assertThat(client, is(not(nullValue())));
    expectedException.expect(ClientNotFoundException.class);

    connectionManager.lookupClient(new ClientIdentifier(CONTEXT, "some other name"));
  }

  @Test
  public void lookupCorrectClientInWrongContext() throws Exception {
    HttpClient client = connectionManager.create(new HttpClientConfiguration.Builder().setName(NAME).build(),
                                                 CONTEXT,
                                                 mock(SchedulerService.class),
                                                 mock(SchedulerConfig.class));

    assertThat(client, is(not(nullValue())));
    expectedException.expect(ClientNotFoundException.class);

    connectionManager.lookupClient(new ClientIdentifier("some other context", NAME));
  }
}
