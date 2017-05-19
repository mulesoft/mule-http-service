/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.services.http.impl.service.server.grizzly;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.mule.service.http.api.HttpConstants.Protocol.HTTPS;
import org.mule.runtime.api.tls.TlsContextFactory;
import org.mule.service.http.api.server.HttpServer;
import org.mule.service.http.api.server.ServerAddress;
import org.mule.services.http.impl.service.server.DefaultServerAddress;
import org.mule.services.http.impl.service.server.ServerIdentifier;

import java.io.IOException;

import org.junit.Test;

public class HttpsGrizzlyServerManagerTestCase extends AbstractGrizzlyServerManagerTestCase {

  private final TlsContextFactory tlsContextFactory = TlsContextFactory.builder().buildDefault();

  @Override
  protected HttpServer getServer(ServerAddress address, ServerIdentifier id) throws IOException {
    return serverManager.createSslServerFor(tlsContextFactory, () -> muleContext.getSchedulerService().ioScheduler(), address,
                                            true,
                                            (int) SECONDS.toMillis(DEFAULT_TEST_TIMEOUT_SECS),
                                            id);
  }

  @Test
  public void sslServerIsHttps() throws Exception {
    final HttpServer createdServer = getServer(new DefaultServerAddress("0.0.0.0", listenerPort.getNumber()),
                                               new ServerIdentifier("context", "name"));
    assertThat(createdServer.getProtocol(), is(HTTPS));
    createdServer.dispose();
  }

}
