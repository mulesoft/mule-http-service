/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.service.http.impl.functional.client;

import static org.hamcrest.Matchers.instanceOf;
import org.mule.runtime.api.lifecycle.CreateException;
import org.mule.runtime.api.util.concurrent.Latch;
import org.mule.runtime.http.api.client.HttpClient;
import org.mule.runtime.http.api.client.HttpClientConfiguration;
import org.mule.runtime.http.api.client.HttpRequestOptions;
import org.mule.runtime.http.api.domain.message.request.HttpRequest;
import org.mule.runtime.http.api.domain.message.response.HttpResponse;

import java.io.IOException;
import java.util.concurrent.ExecutionException;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class HttpClientMaxConnectionsTestCase extends AbstractHttpClientTestCase {

  private static final String ERROR_MESSAGE = "Connection limit exceeded, cannot process request";

  @Rule
  public ExpectedException expectedException = ExpectedException.none();

  private Latch handling = new Latch();
  private Latch complete = new Latch();
  private HttpClient client;

  public HttpClientMaxConnectionsTestCase(String serviceToLoad) {
    super(serviceToLoad);
  }

  @Before
  public void createClient() throws CreateException {
    client = service.getClientFactory().create(new HttpClientConfiguration.Builder()
        .setMaxConnections(1)
        .setName("max-connections-test")
        .build());
    client.start();
  }

  @After
  public void stopClient() {
    if (client != null) {
      client.stop();
    }
  }

  @Override
  protected HttpResponse setUpHttpResponse(HttpRequest request) {
    handling.release();
    try {
      complete.await();
    } catch (InterruptedException e) {
      // Do nothing
    }
    return HttpResponse.builder().build();
  }

  @Test
  public void failsOnConnectionsExceededWhenSync() throws Exception {
    try {
      client.sendAsync(getRequest());
      handling.await();
      expectedException.expect(IOException.class);
      expectedException.expectMessage(ERROR_MESSAGE);
      client.send(getRequest(), HttpRequestOptions.builder().responseTimeout(1).build());
    } finally {
      complete.release();
    }
  }

  @Test
  public void failsOnConnectionsExceededWhenAsync() throws Exception {
    try {
      client.sendAsync(getRequest());
      handling.await();
      expectedException.expect(ExecutionException.class);
      expectedException.expectCause(instanceOf(IOException.class));
      expectedException.expectMessage(ERROR_MESSAGE);
      client.sendAsync(getRequest(), HttpRequestOptions.builder().responseTimeout(1).build()).get();
    } finally {
      complete.release();
    }
  }

  private HttpRequest getRequest() {
    return HttpRequest.builder().uri(getUri()).build();
  }

}
