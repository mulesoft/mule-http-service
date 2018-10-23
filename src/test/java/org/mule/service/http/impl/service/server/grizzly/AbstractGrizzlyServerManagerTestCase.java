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
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mule.runtime.http.api.HttpConstants.ALL_INTERFACES_ADDRESS;
import static org.mule.runtime.http.api.HttpConstants.HttpStatus.OK;

import org.mule.runtime.http.api.domain.message.response.HttpResponse;
import org.mule.runtime.http.api.server.HttpServer;
import org.mule.runtime.http.api.server.ServerAddress;
import org.mule.runtime.http.api.server.ServerCreationException;
import org.mule.runtime.http.api.server.ServerNotFoundException;
import org.mule.runtime.http.api.server.async.ResponseStatusCallback;
import org.mule.runtime.http.api.tcp.TcpServerSocketProperties;
import org.mule.service.http.impl.service.server.DefaultServerAddress;
import org.mule.service.http.impl.service.server.HttpListenerRegistry;
import org.mule.service.http.impl.service.server.ServerIdentifier;
import org.mule.tck.junit4.AbstractMuleContextTestCase;
import org.mule.tck.junit4.rule.DynamicPort;

import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.concurrent.ExecutorService;

public abstract class AbstractGrizzlyServerManagerTestCase extends AbstractMuleContextTestCase {

  private static InetAddress SOME_HOST_ADDRESS;
  private static InetAddress OTHER_HOST_ADDRESS;

  @Rule
  public DynamicPort listenerPort = new DynamicPort("listener.port");
  @Rule
  public ExpectedException expectedException = ExpectedException.none();

  private ExecutorService selectorPool;
  private ExecutorService workerPool;
  private ExecutorService idleTimeoutExecutorService;

  protected GrizzlyServerManager serverManager;

  @BeforeClass
  public static void resolveAddresses() throws UnknownHostException {
    SOME_HOST_ADDRESS = InetAddress.getByName("127.0.0.11");
    OTHER_HOST_ADDRESS = InetAddress.getByName("127.0.0.12");
  }

  @Before
  public void before() {
    selectorPool = newCachedThreadPool();
    workerPool = newCachedThreadPool();
    idleTimeoutExecutorService = newCachedThreadPool();
    HttpListenerRegistry registry = new HttpListenerRegistry();
    DefaultTcpServerSocketProperties socketProperties = new DefaultTcpServerSocketProperties();
    serverManager = new GrizzlyServerManager(selectorPool, workerPool, idleTimeoutExecutorService, registry,
                                             socketProperties, getRuntime().availableProcessors());
  }

  @After
  public void after() {
    serverManager.dispose();
    selectorPool.shutdown();
    workerPool.shutdown();
    idleTimeoutExecutorService.shutdown();
  }

  protected abstract HttpServer getServer(ServerAddress address, ServerIdentifier id) throws ServerCreationException;

  @Test
  public void managerDisposeClosesServerOpenConnections() throws Exception {
    final GrizzlyServerManager serverManager =
        new GrizzlyServerManager(selectorPool, workerPool, idleTimeoutExecutorService, new HttpListenerRegistry(),
                                 new DefaultTcpServerSocketProperties(), getRuntime().availableProcessors());

    final HttpServer server =
        serverManager.createServerFor(new DefaultServerAddress(ALL_INTERFACES_ADDRESS, listenerPort.getNumber()),
                                      () -> muleContext.getSchedulerService().ioScheduler(), true,
                                      (int) SECONDS.toMillis(DEFAULT_TEST_TIMEOUT_SECS),
                                      new ServerIdentifier("context", "name"));
    final ResponseStatusCallback responseStatusCallback = mock(ResponseStatusCallback.class);
    server.addRequestHandler("/path", (requestContext, responseCallback) -> {
      responseCallback.responseReady(HttpResponse.builder().statusCode(OK.getStatusCode()).build(),
                                     responseStatusCallback);
    });
    server.start();

    try (Socket clientSocket = new Socket("localhost", listenerPort.getNumber())) {
      final PrintWriter writer = new PrintWriter(clientSocket.getOutputStream());
      writer.println("GET /path HTTP/1.1");
      writer.println("Host: localhost");
      writer.println("");
      writer.flush();

      BufferedReader br = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
      String t;
      while ((t = br.readLine()) != null) {
        if (t.equals("")) {
          break;
        }
      }

      verify(responseStatusCallback, timeout(1000)).responseSendSuccessfully();
      server.stop();
      serverManager.dispose();

      while ((t = br.readLine()) != null) {
        // Empty the buffer
      }
      br.close();
    }
  }

  @Test
  public void canFindServerInSameContext() throws Exception {
    ServerIdentifier identifier = new ServerIdentifier("context", "name");
    final HttpServer createdServer =
        serverManager.createServerFor(new DefaultServerAddress(ALL_INTERFACES_ADDRESS, listenerPort.getNumber()),
                                      () -> muleContext.getSchedulerService().ioScheduler(), true,
                                      (int) SECONDS.toMillis(DEFAULT_TEST_TIMEOUT_SECS),
                                      identifier);
    final HttpServer foundServer = serverManager.lookupServer(new ServerIdentifier("context", "name"));
    assertThat(createdServer.getServerAddress(), is(equalTo(foundServer.getServerAddress())));
    createdServer.dispose();
  }

  @Test
  public void cannotFindServerInDifferentContext() throws Exception {
    String name = "name";
    ServerIdentifier identifier = new ServerIdentifier("context", name);
    HttpServer server = serverManager.createServerFor(new DefaultServerAddress(ALL_INTERFACES_ADDRESS, listenerPort.getNumber()),
                                                      () -> muleContext.getSchedulerService().ioScheduler(), true,
                                                      (int) SECONDS.toMillis(DEFAULT_TEST_TIMEOUT_SECS),
                                                      identifier);
    try {
      expectedException.expect(ServerNotFoundException.class);
      expectedException.expectMessage(is("Server 'name' could not be found."));
      serverManager.lookupServer(new ServerIdentifier("otherContext", name));
    } finally {
      server.dispose();
    }
  }

  @Test
  public void serverWithSameNameInSameContextOverlaps() throws Exception {
    ServerIdentifier identifier = new ServerIdentifier("context", "name");
    DefaultServerAddress serverAddress = new DefaultServerAddress(SOME_HOST_ADDRESS, listenerPort.getNumber());
    HttpServer server = getServer(serverAddress, identifier);
    DefaultServerAddress otherServerAddress = new DefaultServerAddress(OTHER_HOST_ADDRESS, listenerPort.getNumber());
    try {
      assertThat(serverManager.containsServerFor(otherServerAddress, identifier), is(true));
    } finally {
      server.dispose();
    }
  }

  @Test
  public void serverWithSameNameInDifferentContextDoesNotOverlaps() throws Exception {
    String name = "name";
    ServerIdentifier identifier = new ServerIdentifier("context", name);
    DefaultServerAddress serverAddress = new DefaultServerAddress(SOME_HOST_ADDRESS, listenerPort.getNumber());
    HttpServer server = getServer(serverAddress, identifier);
    DefaultServerAddress otherServerAddress = new DefaultServerAddress(OTHER_HOST_ADDRESS, listenerPort.getNumber());
    ServerIdentifier otherIdentifier = new ServerIdentifier("otherContext", name);
    try {
      assertThat(serverManager.containsServerFor(otherServerAddress, otherIdentifier), is(false));
    } finally {
      server.dispose();
    }
  }

  @Test
  public void serverIsRemovedAfterDispose() throws Exception {
    ServerIdentifier identifier = new ServerIdentifier("context", "name");
    DefaultServerAddress serverAddress = new DefaultServerAddress(ALL_INTERFACES_ADDRESS, listenerPort.getNumber());
    HttpServer server = getServer(serverAddress, identifier);
    server.start();
    server.stop();
    server.dispose();
    expectedException.expect(ServerNotFoundException.class);
    expectedException.expectMessage(is("Server 'name' could not be found."));
    serverManager.lookupServer(identifier);
  }

  @Test
  public void onlyOwnerCanStartServer() throws Exception {
    ServerIdentifier identifier = new ServerIdentifier("context", "name");
    DefaultServerAddress serverAddress = new DefaultServerAddress(ALL_INTERFACES_ADDRESS, listenerPort.getNumber());
    HttpServer owner = getServer(serverAddress, identifier);
    HttpServer reference = serverManager.lookupServer(identifier);

    assertThat(owner.isStopped(), is(true));
    assertThat(reference.isStopped(), is(true));

    reference.start();

    assertThat(owner.isStopped(), is(true));
    assertThat(reference.isStopped(), is(true));

    owner.start();

    assertThat(owner.isStopped(), is(false));
    assertThat(reference.isStopped(), is(false));

    owner.stop();
    owner.dispose();
  }

  @Test
  public void onlyOwnerCanStopServer() throws Exception {
    ServerIdentifier identifier = new ServerIdentifier("context", "name");
    DefaultServerAddress serverAddress = new DefaultServerAddress(ALL_INTERFACES_ADDRESS, listenerPort.getNumber());
    HttpServer owner = getServer(serverAddress, identifier);
    HttpServer reference = serverManager.lookupServer(identifier);

    owner.start();

    assertThat(owner.isStopped(), is(false));
    assertThat(reference.isStopped(), is(false));

    reference.stop();

    assertThat(owner.isStopped(), is(false));
    assertThat(reference.isStopped(), is(false));

    owner.stop();

    assertThat(owner.isStopped(), is(true));
    assertThat(reference.isStopped(), is(true));

    owner.dispose();
  }

  @Test
  public void onlyOwnerCanDisposeServer() throws Exception {
    ServerIdentifier identifier = new ServerIdentifier("context", "name");
    DefaultServerAddress serverAddress = new DefaultServerAddress(ALL_INTERFACES_ADDRESS, listenerPort.getNumber());
    HttpServer owner = getServer(serverAddress, identifier);
    HttpServer reference = serverManager.lookupServer(identifier);

    assertThat(serverManager.containsServerFor(serverAddress, identifier), is(true));

    owner.start();
    owner.stop();

    reference.dispose();

    assertThat(serverManager.containsServerFor(serverAddress, identifier), is(true));

    owner.dispose();

    assertThat(serverManager.containsServerFor(serverAddress, identifier), is(false));
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
