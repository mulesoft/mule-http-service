/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.service.http.impl.service.server.grizzly;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.mule.runtime.http.api.HttpConstants.ALL_INTERFACES_ADDRESS;
import static org.mule.runtime.http.api.HttpConstants.Protocol.HTTP;
import static org.mule.runtime.http.api.HttpConstants.Protocol.HTTPS;
import static org.mule.service.http.impl.AllureConstants.HttpFeature.HTTP_SERVICE;
import static org.mule.service.http.impl.AllureConstants.HttpFeature.HttpStory.SERVER_MANAGEMENT;

import org.mule.runtime.api.tls.TlsContextFactory;
import org.mule.runtime.http.api.server.HttpServer;
import org.mule.runtime.http.api.server.ServerAddress;
import org.mule.runtime.http.api.server.ServerCreationException;
import org.mule.service.http.impl.service.server.DefaultServerAddress;
import org.mule.service.http.impl.service.server.ServerIdentifier;

import java.lang.reflect.Field;

import io.qameta.allure.Description;
import io.qameta.allure.Issue;
import org.glassfish.grizzly.nio.transport.TCPNIOTransport;
import org.junit.Test;

import io.qameta.allure.Feature;
import io.qameta.allure.Story;

@Feature(HTTP_SERVICE)
@Story(SERVER_MANAGEMENT)
public class HttpsGrizzlyServerManagerTestCase extends AbstractGrizzlyServerManagerTestCase {

  private final TlsContextFactory tlsContextFactory = TlsContextFactory.builder().buildDefault();

  @Override
  protected HttpServer getServer(ServerAddress address, ServerIdentifier id) throws ServerCreationException {
    return serverManager.createSslServerFor(tlsContextFactory, () -> muleContext.getSchedulerService().ioScheduler(), address,
                                            true,
                                            (int) SECONDS.toMillis(DEFAULT_TEST_TIMEOUT_SECS),
                                            id, () -> muleContext.getConfiguration().getShutdownTimeout());
  }

  @Test
  public void sslServerIsHttps() throws Exception {
    final HttpServer createdServer = getServer(new DefaultServerAddress(ALL_INTERFACES_ADDRESS, listenerPort.getNumber()),
                                               new ServerIdentifier("context", "name"));
    try {
      assertThat(createdServer.getProtocol(), is(HTTPS));
    } finally {
      createdServer.dispose();
    }
  }

  @Test
  public void disableTls() throws Exception {
    final HttpServer createdServer = getServer(new DefaultServerAddress(ALL_INTERFACES_ADDRESS, listenerPort.getNumber()),
                                               new ServerIdentifier("context", "name"));
    try {
      assertThat(createdServer.getProtocol(), is(HTTPS));
      createdServer.disableTls();
      assertThat(createdServer.getProtocol(), is(HTTP));
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

}
