/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.service.http.impl.functional.client;

import org.mule.runtime.http.api.client.HttpRequestOptions;
import org.mule.runtime.http.api.domain.message.request.HttpRequest;
import org.mule.runtime.http.api.domain.message.response.HttpResponse;
import org.mule.runtime.http.api.domain.request.HttpRequestContext;
import org.mule.runtime.http.api.server.HttpServer;
import org.mule.runtime.http.api.server.HttpServerConfiguration;
import org.mule.runtime.http.api.server.RequestHandler;
import org.mule.runtime.http.api.server.async.HttpResponseReadyCallback;
import org.mule.service.http.impl.functional.AbstractHttpServiceTestCase;
import org.mule.tck.junit4.rule.DynamicPort;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;

/**
 * Sets up the service and a server that can be configured by subclasses to return specific responses, handling all lifecycle.
 */
public abstract class AbstractHttpClientTestCase extends AbstractHttpServiceTestCase {

  protected static final int TIMEOUT = 10000;

  @Rule
  public DynamicPort port = new DynamicPort("port");

  protected HttpServer server;

  public AbstractHttpClientTestCase(String serviceToLoad) {
    super(serviceToLoad);
  }

  @Before
  public void setUp() throws Exception {
    server = service.getServerFactory().create(getServerConfigurationBuilder().build());
    server.start();
    server.addRequestHandler("/*", getRequestHandler());
  }

  /**
   * @return the basic configuration of the test server so subclasses can customize it
   */
  protected HttpServerConfiguration.Builder getServerConfigurationBuilder() throws Exception {
    return new HttpServerConfiguration.Builder().setHost("localhost").setPort(port.getNumber()).setName("client-test-server");
  }

  /**
   * @param request the {@link HttpRequest} received by the server
   * @return the {@link HttpResponse} to return
   */
  protected abstract HttpResponse setUpHttpResponse(HttpRequest request);

  /**
   * @return the server's URI
   */
  protected String getUri() {
    return "http://localhost:" + port.getValue();
  }

  @After
  public void tearDown() throws Exception {
    if (server != null) {
      server.stop();
      server.dispose();
    }
  }

  protected HttpRequestOptions getDefaultOptions(int responseTimeout) {
    return HttpRequestOptions.builder().responseTimeout(responseTimeout).build();
  }

  protected RequestHandler getRequestHandler() {
    return (requestContext, responseCallback) -> responseCallback
        .responseReady(setUpHttpResponse(requestContext.getRequest()),
                       new IgnoreResponseStatusCallback());
  }

}
