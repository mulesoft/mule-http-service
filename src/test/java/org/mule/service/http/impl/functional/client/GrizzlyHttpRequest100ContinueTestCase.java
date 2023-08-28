/*
 * Copyright 2023 Salesforce, Inc. All rights reserved.
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.service.http.impl.functional.client;

import io.qameta.allure.Issue;
import io.qameta.allure.Description;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mule.runtime.http.api.client.HttpClient;
import org.mule.runtime.http.api.client.HttpClientConfiguration;
import org.mule.runtime.http.api.domain.entity.ByteArrayHttpEntity;
import org.mule.runtime.http.api.domain.message.request.HttpRequest;
import org.mule.runtime.http.api.domain.message.response.HttpResponse;

import java.io.IOException;
import java.util.concurrent.TimeoutException;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.mule.runtime.http.api.HttpConstants.HttpStatus.OK;

public class GrizzlyHttpRequest100ContinueTestCase extends AbstractHttpClientTestCase {

  private static final String RESPONSE = "Messi, Messi, Messi, fubol, fubol, fubol";

  private HttpClient client;

  public GrizzlyHttpRequest100ContinueTestCase(String serviceToLoad) {
    super(serviceToLoad);
  }

  @Before
  public void createClient() {
    client = service.getClientFactory().create(new HttpClientConfiguration.Builder()
        .setName("ignore-100-continue")
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
  @Issue("W-13806048")
  @Description("If the server sends an 'Expect' header, it should be ignored and not fail")
  public void expect100isIgnored() throws IOException, TimeoutException {
    HttpResponse response = client.send(HttpRequest.builder().uri(getUri()).build());
    assertThat(response.getStatusCode(), is(OK.getStatusCode()));
    assertThat(response.getEntity().getBytesLength().isPresent(), is(true));
    assertThat(new String(response.getEntity().getBytes()), is(RESPONSE));
  }

  @Override
  protected HttpResponse setUpHttpResponse(HttpRequest request) {
    return HttpResponse.builder().statusCode(OK.getStatusCode()).addHeader("Expect", "100-continue")
        .entity(new ByteArrayHttpEntity(RESPONSE.getBytes())).build();
  }
}
