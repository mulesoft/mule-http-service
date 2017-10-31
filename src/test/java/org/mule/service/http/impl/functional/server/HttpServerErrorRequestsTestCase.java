/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.service.http.impl.functional.server;

import static java.lang.String.format;
import static java.util.Collections.singletonList;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.mule.runtime.api.metadata.MediaType.TEXT;
import static org.mule.runtime.http.api.HttpConstants.HttpStatus.METHOD_NOT_ALLOWED;
import static org.mule.runtime.http.api.HttpConstants.HttpStatus.NOT_FOUND;
import static org.mule.runtime.http.api.HttpConstants.Method.GET;
import static org.mule.runtime.http.api.HttpHeaders.Names.CONTENT_TYPE;
import org.mule.runtime.core.api.util.IOUtils;
import org.mule.runtime.http.api.HttpConstants.HttpStatus;
import org.mule.runtime.http.api.domain.entity.ByteArrayHttpEntity;
import org.mule.runtime.http.api.domain.message.response.HttpResponse;
import org.mule.runtime.http.api.server.HttpServer;
import org.mule.runtime.http.api.server.HttpServerConfiguration;
import org.mule.service.http.impl.functional.AbstractHttpServiceTestCase;
import org.mule.tck.junit4.rule.DynamicPort;

import java.net.URI;

import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public class HttpServerErrorRequestsTestCase extends AbstractHttpServiceTestCase {

  @Rule
  public DynamicPort port = new DynamicPort("port");

  private HttpServer server;

  public HttpServerErrorRequestsTestCase(String serviceToLoad) {
    super(serviceToLoad);
  }

  @Before
  public void setUp() throws Exception {
    server = service.getServerFactory().create(new HttpServerConfiguration.Builder()
        .setHost("localhost")
        .setPort(port.getNumber())
        .setName("errors-test")
        .build());
    server.start();
    server.addRequestHandler(singletonList(GET.name()), "/test", (requestContext, responseCallback) -> {
      responseCallback.responseReady(HttpResponse.builder().entity(new ByteArrayHttpEntity("Success!".getBytes()))
          .addHeader(CONTENT_TYPE, TEXT.toRfcString())
          .build(), new IgnoreResponseStatusCallback());

    });
  }

  @After
  public void tearDown() {
    if (server != null) {
      server.stop();
    }
  }

  @Test
  public void methodNotAllowed() throws Exception {
    verifyErrorResponse(new HttpPost(), "test", METHOD_NOT_ALLOWED, format("Method not allowed for endpoint: http://localhost:%s/test", port.getValue()));
  }

  @Test
  public void notFound() throws Exception {
    verifyErrorResponse(new HttpGet(), "wat", NOT_FOUND, format("No listener for endpoint: http://localhost:%s/wat", port.getValue()));
  }

  private void verifyErrorResponse(HttpRequestBase httpRequest, String path, HttpStatus expectedStatus, String expectedBody)
      throws Exception {
    try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
      httpRequest.setURI(getUri(path));
      try (CloseableHttpResponse response = httpClient.execute(httpRequest)) {
        assertThat(response.getStatusLine().getStatusCode(), is(expectedStatus.getStatusCode()));
        assertThat(response.getStatusLine().getReasonPhrase(), is(expectedStatus.getReasonPhrase()));
        assertThat(response.getFirstHeader(CONTENT_TYPE).getValue(), is(TEXT.toRfcString()));
        assertThat(IOUtils.toString(response.getEntity().getContent()), is(expectedBody));
      }
    }
  }

  private URI getUri(String path) {
    return URI.create(format("http://localhost:%s/%s", port.getValue(), path));
  }

}
