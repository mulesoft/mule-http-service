/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.service.http.impl.service.server.grizzly;

import static java.lang.Integer.getInteger;
import static java.lang.Integer.parseInt;
import static java.lang.Runtime.getRuntime;
import static java.util.concurrent.Executors.newCachedThreadPool;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.glassfish.grizzly.http.util.MimeHeaders.MAX_NUM_HEADERS_DEFAULT;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mule.runtime.api.util.MuleSystemProperties.SYSTEM_PROPERTY_PREFIX;
import static org.mule.runtime.http.api.HttpConstants.ALL_INTERFACES_ADDRESS;
import static org.mule.runtime.http.api.HttpConstants.Protocol.HTTP;
import static org.mule.runtime.http.api.HttpConstants.Protocol.HTTPS;
import static org.mule.runtime.http.api.server.HttpServerProperties.MAX_RESPONSE_HEADERS_KEY;
import static org.mule.service.http.impl.AllureConstants.HttpFeature.HTTP_SERVICE;
import static org.mule.service.http.impl.AllureConstants.HttpFeature.HttpStory.SERVER_MANAGEMENT;

import org.mule.runtime.api.security.Authentication;
import org.mule.runtime.api.tls.TlsContextFactory;
import org.mule.runtime.http.api.server.HttpServer;
import org.mule.runtime.http.api.server.ServerAddress;
import org.mule.runtime.http.api.server.ServerCreationException;
import org.mule.runtime.http.api.tcp.TcpServerSocketProperties;
import org.mule.service.http.impl.service.server.DefaultServerAddress;
import org.mule.service.http.impl.service.server.HttpListenerRegistry;
import org.mule.service.http.impl.service.server.ServerAddressMap;
import org.mule.service.http.impl.service.server.ServerIdentifier;
import org.mule.tck.junit4.rule.SystemProperty;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.concurrent.ExecutorService;

import org.glassfish.grizzly.http.HttpClientFilter;
import org.glassfish.grizzly.http.HttpServerFilter;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import io.qameta.allure.Feature;
import io.qameta.allure.Story;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.MockitoJUnitRunner;

@Feature(HTTP_SERVICE)
@Story(SERVER_MANAGEMENT)
@RunWith(MockitoJUnitRunner.class)
public class HttpGrizzlyServerManagerTestCase extends AbstractGrizzlyServerManagerTestCase {

  private final TlsContextFactory tlsContextFactory = TlsContextFactory.builder().buildDefault();

  GrizzlyServerManager grizzlyServerManager;

  @Rule
  public SystemProperty maxRequestHeadersProperty = new SystemProperty(SYSTEM_PROPERTY_PREFIX + "http.MAX_REQUEST_HEADERS", "10");

  @Rule
  public SystemProperty maxResponseHeadersProperty =
      new SystemProperty(SYSTEM_PROPERTY_PREFIX + "http.MAX_RESPONSE_HEADERS", "8");

  @Before
  public void setUp() {
    grizzlyServerManager = new GrizzlyServerManager(newCachedThreadPool(), newCachedThreadPool(),
                                                    newCachedThreadPool(), new HttpListenerRegistry(),
                                                    new DefaultTcpServerSocketProperties(),
                                                    getRuntime().availableProcessors());
  }

  @After
  public void tearDown() {
    grizzlyServerManager.dispose();
    maxRequestHeadersProperty = new SystemProperty(SYSTEM_PROPERTY_PREFIX + "http.MAX_REQUEST_HEADERS", String.valueOf(MAX_NUM_HEADERS_DEFAULT));
    maxResponseHeadersProperty = new SystemProperty(SYSTEM_PROPERTY_PREFIX + "http.MAX_RESPONSE_HEADERS", String.valueOf(MAX_NUM_HEADERS_DEFAULT));
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

  @Test
  public void testMaxRequestAndResponseHeadersCanBeSetBySystemProperty()
      throws ServerCreationException, NoSuchFieldException, IllegalAccessException {
    ServerAddress serverAddress = new DefaultServerAddress(ALL_INTERFACES_ADDRESS, listenerPort.getNumber());
    grizzlyServerManager.createServerFor(serverAddress, () -> muleContext.getSchedulerService().ioScheduler(), true,
                                         (int) SECONDS.toMillis(DEFAULT_TEST_TIMEOUT_SECS),
                                         new ServerIdentifier("context", "name"),
                                         () -> muleContext.getConfiguration().getShutdownTimeout());

    Field httpServerFilterDelegate = GrizzlyServerManager.class.getDeclaredField("httpServerFilterDelegate");
    httpServerFilterDelegate.setAccessible(true);
    GrizzlyAddressDelegateFilter grizzlyAddressDelegateFilter = (GrizzlyAddressDelegateFilter) httpServerFilterDelegate.get(grizzlyServerManager);

    Field filters = GrizzlyAddressDelegateFilter.class.getDeclaredField("filters");
    filters.setAccessible(true);
    ServerAddressMap serverAddressMap = (ServerAddressMap) filters.get(grizzlyAddressDelegateFilter);

    HttpServerFilter httpServerFilter = (HttpServerFilter) serverAddressMap.get(serverAddress);
    Field maxRequestHeadersField = HttpServerFilter.class.getDeclaredField("maxRequestHeaders");
    maxRequestHeadersField.setAccessible(true);
    int maxRequestHeaders = (int) maxRequestHeadersField.get(httpServerFilter);

    Field maxResponseHeadersField = HttpServerFilter.class.getDeclaredField("maxResponseHeaders");
    maxResponseHeadersField.setAccessible(true);
    int maxResponseHeaders = (int) maxResponseHeadersField.get(httpServerFilter);

    assertThat(maxRequestHeaders, is(parseInt(maxRequestHeadersProperty.getValue())));
    assertThat(maxResponseHeaders, is(parseInt(maxResponseHeadersProperty.getValue())));
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
