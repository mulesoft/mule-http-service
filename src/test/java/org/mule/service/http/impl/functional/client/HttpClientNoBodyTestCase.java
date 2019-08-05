/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.service.http.impl.functional.client;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.mule.runtime.http.api.HttpConstants.HttpStatus.NOT_MODIFIED;
import static org.mule.runtime.http.api.HttpConstants.HttpStatus.NO_CONTENT;
import static org.mule.runtime.http.api.HttpConstants.HttpStatus.RESET_CONTENT;
import org.mule.runtime.http.api.HttpConstants.HttpStatus;
import org.mule.runtime.http.api.client.HttpClient;
import org.mule.runtime.http.api.client.HttpClientConfiguration;
import org.mule.runtime.http.api.domain.message.request.HttpRequest;
import org.mule.runtime.http.api.domain.message.response.HttpResponse;
import org.mule.runtime.http.api.domain.message.response.HttpResponseBuilder;

import java.io.IOException;
import java.util.concurrent.TimeoutException;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class HttpClientNoBodyTestCase extends AbstractHttpClientTestCase {

  private HttpClient client;

  public HttpClientNoBodyTestCase(String serviceToLoad) {
    super(serviceToLoad);
  }

  @Override
  protected HttpResponse setUpHttpResponse(HttpRequest request) {
    HttpResponseBuilder response = HttpResponse.builder();
    if (request.getPath().endsWith("noContent")) {
      response.statusCode(NO_CONTENT.getStatusCode());
    } else if (request.getPath().endsWith("resetContent")) {
      response.statusCode(RESET_CONTENT.getStatusCode());
    } else if (request.getPath().endsWith("notModified")) {
      response.statusCode(NOT_MODIFIED.getStatusCode());
    }
    return response.build();
  }

  @Before
  public void createClient() {
    client = service.getClientFactory().create(new HttpClientConfiguration.Builder()
        .setName("no-body-test")
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
  public void noContentReturnsEmptyBody() throws Exception {
    assertEmptyOnStatus("/noContent", NO_CONTENT);
  }

  @Test
  public void resetContentReturnsEmptyBody() throws Exception {
    assertEmptyOnStatus("/resetContent", RESET_CONTENT);
  }

  @Test
  public void notModifiedReturnsEmptyBody() throws Exception {
    assertEmptyOnStatus("/notModified", NOT_MODIFIED);
  }

  private void assertEmptyOnStatus(String path, HttpStatus expectedStatus) throws IOException, TimeoutException {
    HttpResponse response = client.send(HttpRequest.builder().uri(getUri() + path).build(), TIMEOUT, true, null);
    assertThat(response.getStatusCode(), is(expectedStatus.getStatusCode()));
    assertThat(response.getEntity().getBytesLength().isPresent(), is(true));
    assertThat(response.getEntity().getBytesLength().getAsLong(), is(0L));
  }

}
