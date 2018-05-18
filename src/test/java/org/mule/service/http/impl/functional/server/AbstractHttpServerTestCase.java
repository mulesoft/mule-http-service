/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.service.http.impl.functional.server;

import org.mule.runtime.http.api.server.HttpServer;
import org.mule.runtime.http.api.server.HttpServerConfiguration;
import org.mule.service.http.impl.functional.AbstractHttpServiceTestCase;
import org.mule.tck.junit4.rule.DynamicPort;

import org.junit.After;
import org.junit.Rule;

public abstract class AbstractHttpServerTestCase extends AbstractHttpServiceTestCase {

  @Rule
  public DynamicPort port = new DynamicPort("port");

  protected HttpServer server;

  public AbstractHttpServerTestCase(String serviceToLoad) {
    super(serviceToLoad);
  }

  /**
   * Sets up a server listening in localhost and the dynamic port, with a specific name and allowing further configuration.
   * Subclasses should use this in their before method.
   */
  protected void setUpServer() throws Exception {
    server = service.getServerFactory().create(configureServer(new HttpServerConfiguration.Builder()
        .setHost("localhost")
        .setPort(port.getNumber())
        .setName(getServerName()))
            .build());
    server.start();
  }

  /**
   * @return the name to use for the server
   */
  protected abstract String getServerName();

  /**
   * @param builder the pre-configured builder to work with
   * @return the modified final builder to use
   */
  protected HttpServerConfiguration.Builder configureServer(HttpServerConfiguration.Builder builder) {
    return builder;
  }

  @After
  public void tearDown() {
    if (server != null) {
      server.stop();
      server.dispose();
    }
  }

}
