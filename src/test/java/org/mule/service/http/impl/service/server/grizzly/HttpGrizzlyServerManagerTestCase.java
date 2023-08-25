/*
 * Copyright 2023 Salesforce, Inc. All rights reserved.
 */
package org.mule.service.http.impl.service.server.grizzly;

import static java.lang.Integer.parseInt;
import static java.lang.Runtime.getRuntime;
import static java.util.concurrent.Executors.newCachedThreadPool;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.glassfish.grizzly.http.util.MimeHeaders.MAX_NUM_HEADERS_DEFAULT;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mule.runtime.api.util.MuleSystemProperties.SYSTEM_PROPERTY_PREFIX;
import static org.mule.runtime.http.api.HttpConstants.ALL_INTERFACES_ADDRESS;
import static org.mule.runtime.http.api.HttpConstants.Protocol.HTTP;
import static org.mule.runtime.http.api.HttpConstants.Protocol.HTTPS;
import static org.mule.service.http.impl.AllureConstants.HttpFeature.HTTP_SERVICE;
import static org.mule.service.http.impl.AllureConstants.HttpFeature.HttpStory.SERVER_MANAGEMENT;
import static org.mule.tck.junit4.rule.SystemProperty.callWithProperty;

import org.mule.runtime.api.tls.TlsContextFactory;
import org.mule.runtime.http.api.server.HttpServer;
import org.mule.runtime.http.api.server.ServerAddress;
import org.mule.runtime.http.api.server.ServerCreationException;
import org.mule.runtime.http.api.tcp.TcpServerSocketProperties;
import org.mule.service.http.impl.service.server.DefaultServerAddress;
import org.mule.service.http.impl.service.server.HttpListenerRegistry;
import org.mule.service.http.impl.service.server.ServerAddressMap;
import org.mule.service.http.impl.service.server.ServerIdentifier;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.concurrent.ExecutorService;

import io.qameta.allure.Description;
import io.qameta.allure.Issue;
import org.glassfish.grizzly.nio.transport.TCPNIOTransport;
import org.glassfish.grizzly.http.HttpServerFilter;
import org.junit.After;
import org.junit.Test;

import io.qameta.allure.Feature;
import io.qameta.allure.Story;

@Feature(HTTP_SERVICE)
@Story(SERVER_MANAGEMENT)
public class HttpGrizzlyServerManagerTestCase extends AbstractGrizzlyServerManagerTestCase {

  private final TlsContextFactory tlsContextFactory = TlsContextFactory.builder().buildDefault();

  @After
  public void tearDown() {
    GrizzlyServerManager.refreshSystemProperties();
  }

  @Override
  protected HttpServer getServer(ServerAddress address, ServerIdentifier id) throws ServerCreationException {
    return serverManager.createServerFor(address, () -> muleContext.getSchedulerService().ioScheduler(), true,
                                         (int) SECONDS.toMillis(DEFAULT_TEST_TIMEOUT_SECS), id,
                                         () -> muleContext.getConfiguration().getShutdownTimeout());
  }

  @Test
  public void serverIsHttp() throws Exception {
    final HttpServer createdServer = getServer(new DefaultServerAddress(ALL_INTERFACES_ADDRESS, listenerPort.getNumber()),
                                               new ServerIdentifier("context", "name"));
    try {
      assertThat(createdServer.getProtocol(), is(HTTP));
    } finally {
      createdServer.dispose();
    }
  }

  @Test
  public void enableTls() throws Exception {
    final HttpServer createdServer = getServer(new DefaultServerAddress(ALL_INTERFACES_ADDRESS, listenerPort.getNumber()),
                                               new ServerIdentifier("context", "name"));
    try {
      assertThat(createdServer.getProtocol(), is(HTTP));
      createdServer.enableTls(tlsContextFactory);
      assertThat(createdServer.getProtocol(), is(HTTPS));
    } finally {
      createdServer.dispose();
    }
  }

  @Test
  @Issue("MULE-19779")
  @Description("Tests that by default the read timeout field of TCPNIOTransport is set to 30 seconds")
  public void setDefaultReadTimeoutTo30secs() throws Exception {
    final HttpServer createdServer = getServer(new DefaultServerAddress(ALL_INTERFACES_ADDRESS, listenerPort.getNumber()),
                                               new ServerIdentifier("context", "name"));
    try {
      Field transportField = GrizzlyServerManager.class.getDeclaredField("transport");
      transportField.setAccessible(true);
      TCPNIOTransport tcpnioTransport = (TCPNIOTransport) transportField.get(serverManager);
      assertThat(tcpnioTransport.getReadTimeout(MILLISECONDS), is(30000L));
    } finally {
      createdServer.dispose();
    }
  }

  @Test
  @Issue("MULE-19779")
  @Description("Tests that, when specified, the read timeout field of TCPNIOTransport is set to a custom value")
  public void setCustomReadTimeoutTo20secs() throws Exception {
    long readTimeout = 20000L;
    final HttpServer createdServer =
        serverManager.createServerFor(new DefaultServerAddress(ALL_INTERFACES_ADDRESS, listenerPort.getNumber()),
                                      () -> muleContext.getSchedulerService().ioScheduler(), true,
                                      (int) SECONDS.toMillis(DEFAULT_TEST_TIMEOUT_SECS), new ServerIdentifier("context", "name"),
                                      () -> muleContext.getConfiguration().getShutdownTimeout(), readTimeout);
    try {
      Field transportField = GrizzlyServerManager.class.getDeclaredField("transport");
      transportField.setAccessible(true);
      TCPNIOTransport tcpnioTransport = (TCPNIOTransport) transportField.get(serverManager);
      assertThat(tcpnioTransport.getReadTimeout(MILLISECONDS), is(readTimeout));
    } finally {
      createdServer.dispose();
    }
  }

  @Test
  public void symmetricUsageOfWorkManagerSourceExecutorProviderOnStartAndStop() throws ServerCreationException, IOException {
    final HttpServer createdServer = getServer(new DefaultServerAddress(ALL_INTERFACES_ADDRESS, listenerPort.getNumber()),
                                               new ServerIdentifier("context", "name"));

    int adds = 0, removes = 0;
    assertProviderUsage(adds, removes);

    for (int i = 0; i < 3; ++i) {
      createdServer.start();
      adds += 1;
      assertProviderUsage(adds, removes);

      createdServer.stop();
      removes += 1;
      assertProviderUsage(adds, removes);
    }

    createdServer.dispose();
    assertProviderUsage(adds, removes);
  }

  private void assertProviderUsage(int adds, int removes) {
    verify(((HttpGrizzlyServerManagerTestDecorator) serverManager).getSpiedExecutorProvider(),
           times(adds)).addExecutor(any(), any());
    verify(((HttpGrizzlyServerManagerTestDecorator) serverManager).getSpiedExecutorProvider(),
           times(removes)).removeExecutor(any());
  }

  @Issue("MULE-19837")
  @Description("When the max number of request and response headers are NOT set by System Properties, they should be " +
      "assigned correctly by default. We check that the variables are properly set because we are delegating the max headers" +
      " amount check to Grizzly")
  @Test
  public void testMaxRequestAndResponseHeadersIfNotSetBySystemPropertyAreSetByDefault()
      throws Throwable {
    GrizzlyServerManager.refreshSystemProperties();
    HttpServerFilter httpServerFilter = getHttpServerFilter(serverManager);

    assertThat(getMaxHeaders(httpServerFilter, "maxRequestHeaders"), is(MAX_NUM_HEADERS_DEFAULT));
    assertThat(getMaxHeaders(httpServerFilter, "maxResponseHeaders"), is(MAX_NUM_HEADERS_DEFAULT));
  }

  @Issue("MULE-19837")
  @Description("When the max number of request headers are set by System Properties, they should be " +
      "assigned correctly. We check that the variables are properly set because we are delegating the max headers" +
      " amount check to Grizzly")
  @Test
  public void testMaxRequestHeadersCanBeSetBySystemProperty()
      throws Throwable {
    String maxSetRequestHeaders = "80";

    GrizzlyServerManager grizzlyServerManager =
        callWithProperty(SYSTEM_PROPERTY_PREFIX + "http.MAX_SERVER_REQUEST_HEADERS", maxSetRequestHeaders,
                         this::refreshSystemPropertiesAndCreateServerManager);
    HttpServerFilter httpServerFilter = getHttpServerFilter(grizzlyServerManager);

    assertThat(getMaxHeaders(httpServerFilter, "maxRequestHeaders"), is(parseInt(maxSetRequestHeaders)));
  }

  @Issue("MULE-19837")
  @Description("When the max number of response headers are set by System Properties, they should be " +
      "assigned correctly. We check that the variables are properly set because we are delegating the max headers" +
      " amount check to Grizzly")
  @Test
  public void testMaxResponseHeadersCanBeSetBySystemProperty()
      throws Throwable {
    String maxSetResponseHeaders = "70";

    GrizzlyServerManager grizzlyServerManager =
        callWithProperty(SYSTEM_PROPERTY_PREFIX + "http.MAX_SERVER_RESPONSE_HEADERS", maxSetResponseHeaders,
                         this::refreshSystemPropertiesAndCreateServerManager);
    HttpServerFilter httpServerFilter = getHttpServerFilter(grizzlyServerManager);

    assertThat(getMaxHeaders(httpServerFilter, "maxResponseHeaders"), is(parseInt(maxSetResponseHeaders)));
  }

  private GrizzlyServerManager refreshSystemPropertiesAndCreateServerManager() {
    GrizzlyServerManager.refreshSystemProperties();
    return new GrizzlyServerManager(newCachedThreadPool(), newCachedThreadPool(),
                                    newCachedThreadPool(), new HttpListenerRegistry(),
                                    new DefaultTcpServerSocketProperties(),
                                    getRuntime().availableProcessors());
  }

  private HttpServerFilter getHttpServerFilter(GrizzlyServerManager grizzlyServerManager) throws Throwable {
    ServerAddress serverAddress = new DefaultServerAddress(ALL_INTERFACES_ADDRESS, listenerPort.getNumber());

    grizzlyServerManager.createServerFor(serverAddress, () -> muleContext.getSchedulerService().ioScheduler(), true,
                                         (int) SECONDS.toMillis(DEFAULT_TEST_TIMEOUT_SECS),
                                         new ServerIdentifier("context", "name"),
                                         () -> muleContext.getConfiguration().getShutdownTimeout());

    Field httpServerFilterDelegate = GrizzlyServerManager.class.getDeclaredField("httpServerFilterDelegate");
    httpServerFilterDelegate.setAccessible(true);
    GrizzlyAddressDelegateFilter grizzlyAddressDelegateFilter =
        (GrizzlyAddressDelegateFilter) httpServerFilterDelegate.get(grizzlyServerManager);

    Field filters = GrizzlyAddressDelegateFilter.class.getDeclaredField("filters");
    filters.setAccessible(true);
    ServerAddressMap serverAddressMap = (ServerAddressMap) filters.get(grizzlyAddressDelegateFilter);
    return (HttpServerFilter) serverAddressMap.get(serverAddress);
  }

  private int getMaxHeaders(HttpServerFilter httpServerFilter, String maxHeaders)
      throws NoSuchFieldException, IllegalAccessException {
    Field maxRequestHeadersField = HttpServerFilter.class.getDeclaredField(maxHeaders);
    maxRequestHeadersField.setAccessible(true);
    return (int) maxRequestHeadersField.get(httpServerFilter);
  }

  @Override
  protected HttpGrizzlyServerManagerTestDecorator createServerManager(HttpListenerRegistry registry,
                                                                      DefaultTcpServerSocketProperties socketProperties) {
    return new HttpGrizzlyServerManagerTestDecorator(selectorPool, workerPool, idleTimeoutExecutorService, registry,
                                                     socketProperties, getRuntime().availableProcessors());
  }

  private static class HttpGrizzlyServerManagerTestDecorator extends GrizzlyServerManager {


    WorkManagerSourceExecutorProvider spiedExecutorProvider;

    HttpGrizzlyServerManagerTestDecorator(ExecutorService selectorPool, ExecutorService workerPool,
                                          ExecutorService idleTimeoutExecutorService, HttpListenerRegistry httpListenerRegistry,
                                          TcpServerSocketProperties serverSocketProperties, int selectorCount) {
      super(selectorPool, workerPool, idleTimeoutExecutorService, httpListenerRegistry, serverSocketProperties, selectorCount);
    }

    @Override
    protected WorkManagerSourceExecutorProvider createExecutorProvider() {
      spiedExecutorProvider = spy(new WorkManagerSourceExecutorProvider());
      return spiedExecutorProvider;
    }

    WorkManagerSourceExecutorProvider getSpiedExecutorProvider() {
      return spiedExecutorProvider;
    }
  }
}
