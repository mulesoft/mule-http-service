/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.service.http.impl.functional.server;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mule.runtime.core.api.util.StringUtils.isBlank;
import org.mule.runtime.api.util.concurrent.Latch;
import org.mule.runtime.http.api.domain.entity.InputStreamHttpEntity;
import org.mule.runtime.http.api.domain.message.response.HttpResponse;
import org.mule.runtime.http.api.server.HttpServer;
import org.mule.runtime.http.api.server.HttpServerConfiguration;
import org.mule.runtime.http.api.server.async.ResponseStatusCallback;
import org.mule.service.http.impl.functional.AbstractHttpServiceTestCase;
import org.mule.service.http.impl.functional.FillAndWaitStream;
import org.mule.tck.junit4.rule.DynamicPort;
import org.mule.tck.probe.JUnitProbe;
import org.mule.tck.probe.PollingProber;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.Socket;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public class HttpServerStatusCallbackTestCase extends AbstractHttpServiceTestCase {

  @Rule
  public DynamicPort port = new DynamicPort("port");

  private HttpServer server;
  private Latch latch = new Latch();
  private ResponseStatusCallback statusCallback = spy(new ResponseStatusCallback() {

    @Override
    public void responseSendFailure(Throwable throwable) {

    }

    @Override
    public void responseSendSuccessfully() {

    }

    public void onErrorSendingResponse(Throwable throwable) {

    }
  });

  public HttpServerStatusCallbackTestCase(String serviceToLoad) {
    super(serviceToLoad);
  }

  @Before
  public void setUp() throws Exception {
    server = service.getServerFactory().create(new HttpServerConfiguration.Builder()
        .setHost("localhost")
        .setPort(port.getNumber())
        .setName("status-callback-test")
        .build());
    server.start();
    server.addRequestHandler("/test", (requestContext, responseCallback) -> {
      responseCallback.responseReady(HttpResponse.builder().entity(new InputStreamHttpEntity(new FillAndWaitStream(latch)))
          .build(), statusCallback);

    });
  }

  @Test
  public void failedResponseTriggersError() throws Exception {
    Socket socket = new Socket("localhost", port.getNumber());
    sendRequest(socket);
    getResponseStatus(socket);
    socket.close();
    latch.release();

    new PollingProber(2000, 200).check(new JUnitProbe() {

      @Override
      protected boolean test() throws Exception {
        verify(statusCallback, atLeastOnce()).onErrorSendingResponse(any());
        return true;
      }

    });
  }

  private void sendRequest(Socket socket) throws IOException {
    PrintWriter writer = new PrintWriter(socket.getOutputStream());
    writer.println("GET /test HTTP/1.1");
    writer.println("Host: www.example.com");
    writer.println("");
    writer.flush();
  }

  private String getResponseStatus(Socket socket) {
    try {
      StringWriter writer = new StringWriter();
      BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
      if (reader != null) {
        String line;
        while (!isBlank(line = reader.readLine())) {
          writer.append(line).append("\r\n");
        }
      }
      String response = writer.toString();
      return response.length() == 0 ? null : response;
    } catch (IOException e) {
      return null;
    }
  }

}
