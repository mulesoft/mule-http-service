/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.service.http.impl.service.server.grizzly;

import static java.lang.Runtime.getRuntime;
import static java.lang.Thread.currentThread;
import static java.util.concurrent.Executors.newCachedThreadPool;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.apache.http.client.fluent.Request.Get;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mule.runtime.core.api.util.ClassUtils.withContextClassLoader;
import static org.mule.runtime.http.api.HttpConstants.ALL_INTERFACES_ADDRESS;
import static org.mule.runtime.http.api.HttpConstants.HttpStatus.OK;
import static org.mule.tck.probe.PollingProber.DEFAULT_POLLING_INTERVAL;

import org.mule.runtime.api.util.Reference;
import org.mule.runtime.http.api.domain.message.response.HttpResponse;
import org.mule.runtime.http.api.domain.request.HttpRequestContext;
import org.mule.runtime.http.api.server.HttpServer;
import org.mule.runtime.http.api.server.RequestHandler;
import org.mule.runtime.http.api.server.ServerAddress;
import org.mule.runtime.http.api.server.ServerCreationException;
import org.mule.runtime.http.api.server.ServerNotFoundException;
import org.mule.runtime.http.api.server.async.HttpResponseReadyCallback;
import org.mule.runtime.http.api.server.async.ResponseStatusCallback;
import org.mule.runtime.http.api.tcp.TcpServerSocketProperties;
import org.mule.service.http.impl.service.server.DefaultServerAddress;
import org.mule.service.http.impl.service.server.HttpListenerRegistry;
import org.mule.service.http.impl.service.server.ServerIdentifier;
import org.mule.tck.junit4.AbstractMuleContextTestCase;
import org.mule.tck.junit4.rule.DynamicPort;
import org.mule.tck.probe.JUnitLambdaProbe;
import org.mule.tck.probe.PollingProber;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.lang.ref.PhantomReference;
import java.lang.ref.ReferenceQueue;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.concurrent.ExecutorService;

import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public abstract class AbstractGrizzlyServerManagerTestCase extends AbstractMuleContextTestCase {

  private static final int GC_POLLING_TIMEOUT = 10000;
  private static final String TEST_PATH = "/path";

  private static InetAddress SOME_HOST_ADDRESS;
  private static InetAddress OTHER_HOST_ADDRESS;

  @Rule
  public DynamicPort listenerPort = new DynamicPort("listener.port");
  @Rule
  public ExpectedException expectedException = ExpectedException.none();

  protected ExecutorService selectorPool;
  protected ExecutorService workerPool;
  protected ExecutorService idleTimeoutExecutorService;

  protected GrizzlyServerManager serverManager;
  protected HttpListenerRegistry registry;

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
    registry = new HttpListenerRegistry();
    DefaultTcpServerSocketProperties socketProperties = new DefaultTcpServerSocketProperties();
    serverManager = createServerManager(registry, socketProperties);
  }

  protected GrizzlyServerManager createServerManager(HttpListenerRegistry registry,
                                                     DefaultTcpServerSocketProperties socketProperties) {
    return new GrizzlyServerManager(selectorPool, workerPool, idleTimeoutExecutorService, registry,
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
        createServerManager(new HttpListenerRegistry(), new DefaultTcpServerSocketProperties());

    final HttpServer server =
        serverManager.createServerFor(new DefaultServerAddress(ALL_INTERFACES_ADDRESS, listenerPort.getNumber()),
                                      () -> muleContext.getSchedulerService().ioScheduler(), true,
                                      (int) SECONDS.toMillis(DEFAULT_TEST_TIMEOUT_SECS),
                                      new ServerIdentifier("context", "name"),
                                      () -> muleContext.getConfiguration().getShutdownTimeout());
    final ResponseStatusCallback responseStatusCallback = mock(ResponseStatusCallback.class);
    server.addRequestHandler(TEST_PATH, (requestContext, responseCallback) -> {
      responseCallback.responseReady(HttpResponse.builder().statusCode(OK.getStatusCode()).build(),
                                     responseStatusCallback);
    });
    server.start();

    try (Socket clientSocket = new Socket("localhost", listenerPort.getNumber())) {
      final PrintWriter writer = new PrintWriter(clientSocket.getOutputStream());
      writer.println("GET " + TEST_PATH + " HTTP/1.1");
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
                                      identifier, () -> muleContext.getConfiguration().getShutdownTimeout());
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
                                                      identifier, () -> muleContext.getConfiguration().getShutdownTimeout());
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

    RequestHandler requestHandler = new DummyRequestHandler();
    PhantomReference<RequestHandler> requestHandlerRef = new PhantomReference<>(requestHandler, new ReferenceQueue<>());

    server.addRequestHandler(TEST_PATH, requestHandler);
    server.stop();
    server.dispose();

    requestHandler = null;
    new PollingProber(GC_POLLING_TIMEOUT, DEFAULT_POLLING_INTERVAL).check(new JUnitLambdaProbe(() -> {
      System.gc();
      assertThat(requestHandlerRef.isEnqueued(), is(true));
      return true;
    }, "A hard reference is being mantained to the requestHandler."));

    expectedException.expect(ServerNotFoundException.class);
    expectedException.expectMessage(is("Server 'name' could not be found."));
    serverManager.lookupServer(identifier);
  }

  private static final class DummyRequestHandler implements RequestHandler {

    @Override
    public void handleRequest(HttpRequestContext requestContext, HttpResponseReadyCallback responseCallback) {
      // Nothing to do
    }
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

  @Test
  public void requestHandlerIsExecutedWithTheSameClassLoaderItWasAddedWith() throws Exception {
    final GrizzlyServerManager serverManager =
        createServerManager(new HttpListenerRegistry(), new DefaultTcpServerSocketProperties());

    final HttpServer server =
        serverManager.createServerFor(new DefaultServerAddress(ALL_INTERFACES_ADDRESS, listenerPort.getNumber()),
                                      () -> muleContext.getSchedulerService().ioScheduler(), true,
                                      (int) SECONDS.toMillis(DEFAULT_TEST_TIMEOUT_SECS),
                                      new ServerIdentifier("context", "name"),
                                      () -> muleContext.getConfiguration().getShutdownTimeout());
    final ResponseStatusCallback responseStatusCallback = mock(ResponseStatusCallback.class);
    Reference<ClassLoader> requestHandlerExecutionClassLoader = new Reference<>();

    // The request handler is added using this class loader.
    ClassLoader requestHandlerAdditionClassLoader = mock(ClassLoader.class);
    withContextClassLoader(requestHandlerAdditionClassLoader, () -> {
      server.addRequestHandler(TEST_PATH, (requestContext, responseCallback) -> {
        responseCallback.responseReady(HttpResponse.builder().statusCode(OK.getStatusCode()).build(),
                                       responseStatusCallback);

        // We intercept the class loader used on handler execution.
        requestHandlerExecutionClassLoader.set(currentThread().getContextClassLoader());
      });
    });
    server.start();

    // Send a request.
    Get("http://localhost:" + listenerPort.getValue() + TEST_PATH).execute();

    // Both class loaders are the same.
    assertThat(requestHandlerExecutionClassLoader.get(), is(requestHandlerAdditionClassLoader));

    server.stop();
    serverManager.dispose();
  }

  protected class DefaultTcpServerSocketProperties implements TcpServerSocketProperties {

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
