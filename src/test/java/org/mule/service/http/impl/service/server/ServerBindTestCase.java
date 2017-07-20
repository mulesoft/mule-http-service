/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.service.http.impl.service.server;

import static org.mule.service.http.impl.service.AllureConstants.HttpFeature.HTTP_SERVICE;
import org.mule.runtime.http.api.server.HttpServer;
import org.mule.runtime.http.api.server.HttpServerConfiguration;
import org.mule.service.http.impl.service.HttpServiceImplementation;
import org.mule.tck.SimpleUnitTestSupportSchedulerService;
import org.mule.tck.junit4.AbstractMuleTestCase;
import org.mule.tck.junit4.rule.DynamicPort;

import java.net.InetSocketAddress;
import java.net.ServerSocket;

import io.qameta.allure.Feature;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

@Feature(HTTP_SERVICE)
public class ServerBindTestCase extends AbstractMuleTestCase {

  @Rule
  public DynamicPort usedPort = new DynamicPort("usedPort");

  @Rule
  public ExpectedException expectedException = ExpectedException.none();

  private HttpServiceImplementation service = new HttpServiceImplementation(new SimpleUnitTestSupportSchedulerService());
  private ServerSocket serverSocket;

  @Before
  public void setUp() throws Exception {
    service.start();
    serverSocket = new ServerSocket();
    serverSocket.bind(new InetSocketAddress("localhost", usedPort.getNumber()));
  }

  @After
  public void tearDown() throws Exception {
    serverSocket.close();
    service.stop();
  }

  @Test
  public void cannotBindToUsedPort() throws Exception {
    HttpServer server = service.getServerFactory().create(new HttpServerConfiguration.Builder()
        .setHost("localhost")
        .setPort(usedPort.getNumber())
        .setName("failingServer")
        .build());
    try {
      expectedException.expectMessage("Address already in use");
      server.start();
    } finally {
      server.stop();
      server.dispose();
    }
  }

}
