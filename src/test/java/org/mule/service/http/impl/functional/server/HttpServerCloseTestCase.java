/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.service.http.impl.functional.server;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.mock;
import static org.mule.runtime.core.api.util.StringUtils.isEmpty;
import static org.mule.runtime.http.api.HttpConstants.HttpStatus.OK;
import org.mule.runtime.http.api.domain.message.response.HttpResponse;
import org.mule.runtime.http.api.server.HttpServer;
import org.mule.runtime.http.api.server.HttpServerConfiguration;
import org.mule.runtime.http.api.server.async.ResponseStatusCallback;
import org.mule.service.http.impl.functional.AbstractHttpServiceTestCase;
import org.mule.tck.junit4.rule.DynamicPort;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.Socket;

import org.apache.http.HttpVersion;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public class HttpServerCloseTestCase extends AbstractHttpServiceTestCase {

  @Rule
  public DynamicPort port = new DynamicPort("port");

  private HttpServer server;

  public HttpServerCloseTestCase(String serviceToLoad) {
    super(serviceToLoad);
  }

  /**
   * Sets up a server listening in localhost and the dynamic port, with a specific name and allowing further configuration.
   * Subclasses should use this in their before method.
   */
  private void setUpServer() throws Exception {
    server = service.getServerFactory().create(configureServer(new HttpServerConfiguration.Builder()
        .setHost("localhost")
        .setPort(port.getNumber())
        .setName(getServerName()))
            .build());
    server.start();
  }

  /**
   * @param builder the pre-configured builder to work with
   * @return the modified final builder to use
   */
  private HttpServerConfiguration.Builder configureServer(HttpServerConfiguration.Builder builder) {
    return builder;
  }

  private String getServerName() {
    return "close-test";
  }

  @Before
  public void setUp() throws Exception {
    setUpServer();
    final ResponseStatusCallback responseStatusCallback = mock(ResponseStatusCallback.class);
    server.addRequestHandler("/path", (requestContext, responseCallback) -> {
      responseCallback.responseReady(HttpResponse.builder().statusCode(OK.getStatusCode()).build(), responseStatusCallback);
    });
  }

  @After
  public void tearDown() {
    if (server != null) {
      server.stop();
      server.dispose();
    }
  }

  @Test
  public void closeClientConnectionsWhenServerIsStopped() throws IOException {
    try (Socket clientSocket = new Socket("localhost", port.getNumber())) {
      assertThat(clientSocket.isConnected(), is(true));

      sendRequest(clientSocket);
      assertResponse(getResponse(clientSocket), true);

      sendRequest(clientSocket);
      assertResponse(getResponse(clientSocket), true);

      server.stop();

      sendRequest(clientSocket);
      assertResponse(getResponse(clientSocket), false);
    }
  }

  private void sendRequest(Socket socket) throws IOException {
    PrintWriter writer = new PrintWriter(socket.getOutputStream());
    writer.println("GET /path " + HttpVersion.HTTP_1_1);
    writer.println("Host: www.example.com");
    writer.println("");
    writer.flush();
  }

  private String getResponse(Socket socket) {
    try (StringWriter writer = new StringWriter()) {
      BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
      String line;
      while (!isEmpty(line = reader.readLine())) {
        writer.append(line).append("\r\n");
      }
      return writer.toString();
    } catch (IOException e) {
      return null;
    }
  }

  private void assertResponse(String response, boolean shouldBeValid) {
    assertThat(isEmpty(response), is(!shouldBeValid));
  }


}
