/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.service.http.impl.functional.client;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;
import static org.mule.runtime.http.api.HttpConstants.HttpStatus.OK;

import java.util.ArrayList;
import java.util.List;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mule.runtime.api.lifecycle.CreateException;
import org.mule.runtime.api.util.concurrent.Latch;
import org.mule.runtime.http.api.client.HttpClient;
import org.mule.runtime.http.api.client.HttpClientConfiguration;
import org.mule.runtime.http.api.domain.message.request.HttpRequest;
import org.mule.runtime.http.api.domain.message.response.HttpResponse;

public class HttpRequesterQuickConnectionCloseTestCase extends AbstractHttpClientTestCase {

  private static int NUMBER_OF_REQUESTS = 50;

  private HttpClient client;

  private List<HttpResponse> responses = new ArrayList<>(NUMBER_OF_REQUESTS);

  private List<Throwable> errors = new ArrayList<>(NUMBER_OF_REQUESTS);

  public HttpRequesterQuickConnectionCloseTestCase(String serviceToLoad) {
    super(serviceToLoad);
  }

  @Before
  public void createClient() throws CreateException {
    client = service.getClientFactory().create(new HttpClientConfiguration.Builder()
        .setMaxConnections(1)
        .setName("http-requester-quick-connection-close")
        .build());
    client.start();
  }

  @After
  public void stopClient() {
    if (client != null) {
      client.stop();
    }
  }

  @Test
  public void test() {
    for (int i = 0; i < NUMBER_OF_REQUESTS; i++) {
      HttpResponse response;
      try {
        response = client.send(getRequest());
        responses.add(response);
      } catch (Throwable e) {
        errors.add(e);
      }
    }
    assertThat(responses.size(), is(NUMBER_OF_REQUESTS));
    assertThat(errors.size(), is(0));
  }

  @Override
  protected HttpResponse setUpHttpResponse(HttpRequest request) {
    return HttpResponse.builder()
        .statusCode(OK.getStatusCode())
        .reasonPhrase(OK.getReasonPhrase())
        .build();
  }

  private HttpRequest getRequest() {
    return HttpRequest.builder().uri(getUri()).build();
  }
}
