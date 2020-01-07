/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.service.http.impl.service.server.grizzly;

import static java.lang.Runtime.getRuntime;
import static java.util.concurrent.Executors.newCachedThreadPool;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.mock;
import static org.mule.runtime.core.api.util.StringUtils.isEmpty;
import static org.mule.runtime.http.api.HttpConstants.ALL_INTERFACES_ADDRESS;
import static org.mule.runtime.http.api.HttpConstants.HttpStatus.OK;
import org.mule.runtime.http.api.domain.message.response.HttpResponse;
import org.mule.runtime.http.api.server.HttpServer;
import org.mule.runtime.http.api.server.ServerCreationException;
import org.mule.runtime.http.api.server.async.ResponseStatusCallback;
import org.mule.runtime.http.api.tcp.TcpServerSocketProperties;
import org.mule.service.http.impl.service.server.DefaultServerAddress;
import org.mule.service.http.impl.service.server.HttpListenerRegistry;
import org.mule.service.http.impl.service.server.ServerIdentifier;
import org.mule.tck.junit4.AbstractMuleContextTestCase;
import org.mule.tck.junit4.rule.DynamicPort;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.Socket;
import java.util.concurrent.ExecutorService;

import org.apache.http.HttpVersion;
import org.hamcrest.Matchers;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public class GrizzlyHttpServerTestCase extends AbstractMuleContextTestCase {

  @Rule
  public DynamicPort listenerPort = new DynamicPort("listener.port");

  private ExecutorService selectorPool;
  private ExecutorService workerPool;
  private ExecutorService idleTimeoutExecutorService;

  private GrizzlyServerManager serverManager;
  private HttpServer grizzlyHttpServer;

  @Before
  public void setup() throws ServerCreationException {
    selectorPool = newCachedThreadPool();
    workerPool = newCachedThreadPool();
    idleTimeoutExecutorService = newCachedThreadPool();
    HttpListenerRegistry registry = new HttpListenerRegistry();
    DefaultTcpServerSocketProperties socketProperties = new DefaultTcpServerSocketProperties();
    serverManager = new GrizzlyServerManager(selectorPool, workerPool, idleTimeoutExecutorService, registry,
                                             socketProperties, getRuntime().availableProcessors());
    grizzlyHttpServer =
        serverManager.createServerFor(new DefaultServerAddress(ALL_INTERFACES_ADDRESS, listenerPort.getNumber()),
                                      () -> muleContext.getSchedulerService().ioScheduler(), true,
                                      (int) SECONDS.toMillis(DEFAULT_TEST_TIMEOUT_SECS), new ServerIdentifier("context", "name"));
    final ResponseStatusCallback responseStatusCallback = mock(ResponseStatusCallback.class);
    grizzlyHttpServer.addRequestHandler("/path", (requestContext, responseCallback) -> {
      responseCallback.responseReady(HttpResponse.builder().statusCode(OK.getStatusCode()).build(), responseStatusCallback);
    });
  }

  @After
  public void cleanup() {
    if (!grizzlyHttpServer.isStopped()) {
      grizzlyHttpServer.stop();
    }

    serverManager.dispose();
    idleTimeoutExecutorService.shutdown();
    workerPool.shutdown();
    selectorPool.shutdown();
  }

  @Test
  public void closeClientConnectionsWhenServerIsStopped() throws IOException {
    grizzlyHttpServer.start();

    try (Socket clientSocket = new Socket("localhost", listenerPort.getNumber())) {
      assertThat(clientSocket.isConnected(), is(true));

      sendRequest(clientSocket);
      assertResponse(getResponse(clientSocket), true);

      sendRequest(clientSocket);
      assertResponse(getResponse(clientSocket), true);

      grizzlyHttpServer.stop();

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
    assertThat(isEmpty(response), Matchers.is(!shouldBeValid));
  }

  private class DefaultTcpServerSocketProperties implements TcpServerSocketProperties {

    @Override
    public Integer getSendBufferSize() {
      return null;
    }

    @Override
    public Integer getReceiveBufferSize() {
      return null;
    }

    @Override
    public Integer getClientTimeout() {
      return null;
    }

    @Override
    public Boolean getSendTcpNoDelay() {
      return true;
    }

    @Override
    public Integer getLinger() {
      return null;
    }

    @Override
    public Boolean getKeepAlive() {
      return false;
    }

    @Override
    public Boolean getReuseAddress() {
      return true;
    }

    @Override
    public Integer getReceiveBacklog() {
      return 50;
    }

    @Override
    public Integer getServerTimeout() {
      return null;
    }
  }

}
