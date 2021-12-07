/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.service.http.impl.service.server;

import static java.lang.String.format;
import static java.util.Collections.singletonList;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.mule.runtime.api.metadata.MediaType.TEXT;
import static org.mule.runtime.api.util.MuleSystemProperties.SYSTEM_PROPERTY_PREFIX;
import static org.mule.runtime.http.api.HttpConstants.HttpStatus.REQUEST_TOO_LONG;
import static org.mule.runtime.http.api.HttpConstants.Method.GET;
import static org.mule.runtime.http.api.HttpHeaders.Names.CONTENT_TYPE;
import static org.mule.tck.junit4.rule.SystemProperty.callWithProperty;

import org.mule.runtime.http.api.HttpConstants;
import org.mule.runtime.http.api.domain.entity.ByteArrayHttpEntity;
import org.mule.runtime.http.api.domain.message.response.HttpResponse;
import org.mule.runtime.http.api.server.HttpServer;
import org.mule.runtime.http.api.server.HttpServerConfiguration;
import org.mule.service.http.impl.functional.server.AbstractHttpServerTestCase;
import org.mule.service.http.impl.service.server.grizzly.GrizzlyServerManager;

import io.qameta.allure.Description;
import io.qameta.allure.Issue;
import org.apache.http.NoHttpResponseException;
import org.apache.http.StatusLine;
import org.apache.http.client.fluent.Request;
import org.junit.Test;

public class HttpServiceMaxHeadersTestCase extends AbstractHttpServerTestCase {

  private static final String SIMPLE_ENDPOINT = "test";
  private static final String PAYLOAD1 = "p1";

  public HttpServiceMaxHeadersTestCase(String serviceToLoad) {
    super(serviceToLoad);
  }

  @Override
  protected String getServerName() {
    return "max-headers-test";
  }

  private void registerHandler(HttpConstants.Method httpMethod, String endpoint, String payload, HttpServer httpServer) {
    httpServer.addRequestHandler(singletonList(httpMethod.name()), "/" + endpoint, (requestContext, responseCallback) -> {
      responseCallback.responseReady(HttpResponse.builder().entity(new ByteArrayHttpEntity(payload.getBytes()))
          .addHeader(CONTENT_TYPE, TEXT.toRfcString())
          .build(), new IgnoreResponseStatusCallback());
    });
  }

  @Issue("MULE-19837")
  @Description("When the max number of request headers are set by System Properties, they should be " +
      "assigned correctly. If this max number is exceeded, a 413 status code should be returned.")
  @Test
  public void whenRequestHasMoreHeadersThanMaxNumberThen413ShouldBeReturned() throws Throwable {
    HttpServer httpServer = callWithProperty(SYSTEM_PROPERTY_PREFIX + "http.MAX_SERVER_REQUEST_HEADERS", "3",
                                             this::refreshSystemPropertiesAndCreateServer);
    registerHandler(GET, SIMPLE_ENDPOINT, PAYLOAD1, httpServer);

    Request request =
        Request.Get(format("http://%s:%s/%s", httpServer.getServerAddress().getIp(), port.getValue(), SIMPLE_ENDPOINT));
    request.addHeader("header1", "someValue");
    request.addHeader("header2", "someValue");
    request.addHeader("header3", "someValue");
    request.addHeader("header4", "someValue");

    org.apache.http.HttpResponse response = request.execute().returnResponse();
    StatusLine statusLine = response.getStatusLine();

    assertThat(statusLine.getStatusCode(), is(REQUEST_TOO_LONG.getStatusCode()));

    httpServer.stop();
    httpServer.dispose();
  }

  @Issue("MULE-19837")
  @Description("When the max number of response headers are set by System Properties, they should be " +
      "assigned correctly. If this max number is exceeded, a NoHttpResponseException should be thrown")
  @Test(expected = NoHttpResponseException.class)
  public void whenResponseHasMoreHeadersThanMaxNumberThenExceptionShouldBeThrown() throws Throwable {
    HttpServer httpServer = callWithProperty(SYSTEM_PROPERTY_PREFIX + "http.MAX_SERVER_RESPONSE_HEADERS", "2",
                                             this::refreshSystemPropertiesAndCreateServer);
    registerHandler(GET, SIMPLE_ENDPOINT, PAYLOAD1, httpServer);
    Request request =
        Request.Get(format("http://%s:%s/%s", httpServer.getServerAddress().getIp(), port.getValue(), SIMPLE_ENDPOINT));
    try {
      request.execute();
    } finally {
      httpServer.stop();
      httpServer.dispose();
    }
  }

  private HttpServer refreshSystemPropertiesAndCreateServer() throws Exception {
    GrizzlyServerManager.refreshSystemProperties();
    HttpServer httpServer = service.getServerFactory().create(configureServer(new HttpServerConfiguration.Builder()
        .setHost("localhost")
        .setPort(port.getNumber())
        .setName(getServerName()))
            .build());
    httpServer.start();
    return httpServer;
  }
}
