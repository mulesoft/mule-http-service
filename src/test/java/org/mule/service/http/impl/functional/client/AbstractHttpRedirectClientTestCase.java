/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */

package org.mule.service.http.impl.functional.client;

import static org.mule.runtime.http.api.server.HttpServerConfiguration.Builder;

import org.junit.After;
import org.junit.Rule;
import org.mule.runtime.http.api.domain.message.request.HttpRequest;
import org.mule.runtime.http.api.domain.message.response.HttpResponse;
import org.mule.runtime.http.api.server.HttpServer;
import org.mule.runtime.http.api.server.RequestHandler;
import org.mule.runtime.http.api.server.ServerCreationException;
import org.mule.tck.junit4.rule.DynamicPort;

import java.io.IOException;

import static org.mule.runtime.core.api.util.ClassUtils.withContextClassLoader;

public abstract class AbstractHttpRedirectClientTestCase extends AbstractHttpClientTestCase {

  @Rule
  public DynamicPort redirectPort = new DynamicPort("port");

  protected HttpServer redirectServer;

  public AbstractHttpRedirectClientTestCase(String serviceToLoad) {
    super(serviceToLoad);
  }

  @Override
  public void setUp() throws Exception {
    redirectServer = service.getServerFactory().create(getRedirectServerConfigurationBuilder().build());
    redirectServer.start();
    RequestHandler requestHandler = getRedirectRequestHandler();
    withContextClassLoader(requestHandler.getContextClassLoader(), () -> {
      redirectServer.addRequestHandler("/*", requestHandler);
    });
    super.setUp();
  }

  /**
   * @return the basic configuration of the test server so subclasses can customize it
   */
  protected Builder getRedirectServerConfigurationBuilder() {
    return new Builder().setHost("localhost").setPort(redirectPort.getNumber())
        .setName("redirect-client-test-server");
  }

  /**
   * @param request the {@link HttpRequest} received by the server
   * @return the {@link HttpResponse} to return
   */
  protected abstract HttpResponse setUpHttpRedirectResponse(HttpRequest request);

  protected RequestHandler getRedirectRequestHandler() {
    return (requestContext, responseCallback) -> responseCallback
        .responseReady(setUpHttpRedirectResponse(requestContext.getRequest()),
                       new IgnoreResponseStatusCallback());
  }

  /**
   * @return the server's URI
   */
  protected String getRedirectUri() {
    return "http://localhost:" + redirectPort.getValue();
  }

  @After
  public void tearDown() throws Exception {
    if (redirectServer != null) {
      redirectServer.stop();
      redirectServer.dispose();
    }
  }
}
