/*
 * Copyright 2023 Salesforce, Inc. All rights reserved.
 */
package org.mule.service.http.impl.functional.server;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.mule.tck.probe.PollingProber.DEFAULT_POLLING_INTERVAL;
import static org.mule.tck.probe.PollingProber.probe;
import org.mule.runtime.http.api.server.HttpServer;
import org.mule.runtime.http.api.server.HttpServerConfiguration;
import org.mule.runtime.http.api.server.ServerCreationException;
import org.mule.service.http.impl.functional.AbstractHttpServiceTestCase;
import org.mule.tck.junit4.rule.DynamicPort;

import java.io.IOException;
import java.lang.ref.PhantomReference;
import java.lang.ref.ReferenceQueue;
import java.net.Socket;

import org.junit.Rule;
import org.junit.Test;

public class HttpServerLeakTestCase extends AbstractHttpServiceTestCase {

  private static final int GC_POLLING_TIMEOUT = 10000;

  @Rule
  public DynamicPort port = new DynamicPort("port");

  public HttpServerLeakTestCase(String serviceToLoad) {
    super(serviceToLoad);
  }

  @Test
  public void theServerIsNotLeakedEvenAfterReceiveAConnection() throws ServerCreationException, IOException {
    HttpServer server = service.getServerFactory().create(new HttpServerConfiguration.Builder()
        .setHost("localhost")
        .setPort(port.getNumber())
        .setName("NoLeakServer")
        .build());

    PhantomReference<HttpServer> serverRef = new PhantomReference<>(server, new ReferenceQueue<>());
    server.start();

    Socket clientSocket = new Socket("localhost", port.getNumber());
    clientSocket.close();

    server.stop();
    server.dispose();
    server = null;

    probe(GC_POLLING_TIMEOUT, DEFAULT_POLLING_INTERVAL, () -> {
      System.gc();
      assertThat(serverRef.isEnqueued(), is(true));
      return true;
    });
  }
}
