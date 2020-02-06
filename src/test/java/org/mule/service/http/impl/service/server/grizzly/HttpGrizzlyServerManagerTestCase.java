/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.service.http.impl.service.server.grizzly;

import static java.lang.Runtime.getRuntime;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mule.runtime.http.api.HttpConstants.Protocol.HTTP;
import static org.mule.service.http.impl.AllureConstants.HttpFeature.HTTP_SERVICE;
import static org.mule.service.http.impl.AllureConstants.HttpFeature.HttpStory.SERVER_MANAGEMENT;
import org.mule.runtime.http.api.server.HttpServer;
import org.mule.runtime.http.api.server.ServerAddress;
import org.mule.runtime.http.api.server.ServerCreationException;
import org.mule.runtime.http.api.tcp.TcpServerSocketProperties;
import org.mule.service.http.impl.service.server.DefaultServerAddress;
import org.mule.service.http.impl.service.server.HttpListenerRegistry;
import org.mule.service.http.impl.service.server.ServerIdentifier;

import java.io.IOException;
import java.util.concurrent.ExecutorService;

import org.junit.Test;
import io.qameta.allure.Feature;
import io.qameta.allure.Story;

@Feature(HTTP_SERVICE)
@Story(SERVER_MANAGEMENT)
public class HttpGrizzlyServerManagerTestCase extends AbstractGrizzlyServerManagerTestCase {

  @Override
  protected HttpServer getServer(ServerAddress address, ServerIdentifier id) throws ServerCreationException {
    return serverManager.createServerFor(address, () -> muleContext.getSchedulerService().ioScheduler(), true,
                                         (int) SECONDS.toMillis(DEFAULT_TEST_TIMEOUT_SECS), id);
  }

  @Test
  public void serverIsHttp() throws Exception {
    final HttpServer createdServer = getServer(new DefaultServerAddress("0.0.0.0", listenerPort.getNumber()),
                                               new ServerIdentifier("context", "name"));
    try {
      assertThat(createdServer.getProtocol(), is(HTTP));
    } finally {
      createdServer.dispose();
    }
  }

  @Test
  public void symmetricUsageOfWorkManagerSourceExecutorProviderOnStartAndStop() throws ServerCreationException, IOException {
    final HttpServer createdServer = getServer(new DefaultServerAddress("0.0.0.0", listenerPort.getNumber()),
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
