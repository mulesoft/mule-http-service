/*
 * Copyright 2023 Salesforce, Inc. All rights reserved.
 */
package org.mule.service.http.impl.functional.server;

import org.mule.runtime.http.api.server.HttpServer;
import org.mule.runtime.http.api.server.HttpServerConfiguration;
import org.mule.service.http.impl.functional.AbstractHttpServiceTestCase;
import org.mule.tck.junit4.rule.DynamicPort;

import java.net.InetSocketAddress;
import java.net.ServerSocket;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class HttpServerBindTestCase extends AbstractHttpServiceTestCase {

  @Rule
  public DynamicPort usedPort = new DynamicPort("usedPort");

  @Rule
  public ExpectedException expectedException = ExpectedException.none();

  private ServerSocket serverSocket;

  public HttpServerBindTestCase(String serviceToLoad) {
    super(serviceToLoad);
  }

  @Before
  public void setUp() throws Exception {
    serverSocket = new ServerSocket();
    serverSocket.bind(new InetSocketAddress("localhost", usedPort.getNumber()));
  }

  @After
  public void tearDown() throws Exception {
    serverSocket.close();
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
