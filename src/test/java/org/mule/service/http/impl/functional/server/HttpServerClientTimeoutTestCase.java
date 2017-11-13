/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.service.http.impl.functional.server;

import static java.lang.String.format;
import static java.util.Collections.singletonList;
import static java.util.concurrent.Executors.newSingleThreadExecutor;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.junit.Assert.fail;
import static org.mule.runtime.api.metadata.MediaType.TEXT;
import static org.mule.runtime.http.api.HttpConstants.HttpStatus.INTERNAL_SERVER_ERROR;
import static org.mule.runtime.http.api.HttpConstants.Method.GET;
import static org.mule.runtime.http.api.HttpHeaders.Names.CONTENT_TYPE;

import org.mule.runtime.http.api.domain.entity.ByteArrayHttpEntity;
import org.mule.runtime.http.api.domain.message.response.HttpResponse;
import org.mule.runtime.http.api.server.HttpServer;
import org.mule.runtime.http.api.server.HttpServerConfiguration;
import org.mule.runtime.http.api.server.async.ResponseStatusCallback;
import org.mule.service.http.impl.functional.AbstractHttpServiceTestCase;
import org.mule.tck.junit4.rule.DynamicPort;

import org.apache.http.client.fluent.Request;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.net.SocketTimeoutException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;

public class HttpServerClientTimeoutTestCase extends AbstractHttpServiceTestCase {

  private static final int TIMEOUT = 5000;

  @Rule
  public DynamicPort port = new DynamicPort("port");

  private HttpServer server;

  private CountDownLatch requestLatch;
  private CountDownLatch responseLatch;

  public HttpServerClientTimeoutTestCase(String serviceToLoad) {
    super(serviceToLoad);
  }

  @Before
  public void setUp() throws Exception {
    requestLatch = new CountDownLatch(1);
    responseLatch = new CountDownLatch(1);

    server = service.getServerFactory().create(new HttpServerConfiguration.Builder()
        .setHost("localhost")
        .setPort(port.getNumber())
        .setName("client-timeout-test")
        .build());
    server.start();
    server.addRequestHandler(singletonList(GET.name()), "/test", (requestContext, responseCallback) -> {

      try {
        requestLatch.await();
      } catch (InterruptedException e) {
        // Nothing to do
      }

      System.out.println("Continue processing after client timeout");

      responseCallback.responseReady(HttpResponse.builder().statusCode(INTERNAL_SERVER_ERROR.getStatusCode())
          .entity(new ByteArrayHttpEntity("Success!".getBytes()))
          .addHeader(CONTENT_TYPE, TEXT.toRfcString())
          .build(), new ResponseStatusCallback() {

            @Override
            public void responseSendFailure(Throwable throwable) {
              throwable.printStackTrace();
            }

            @Override
            public void responseSendSuccessfully() {
              responseLatch.countDown();
            }
          });
    });
  }

  @After
  public void tearDown() {
    if (server != null) {
      server.stop();
      server.dispose();
    }
  }

  @Test
  public void executingRequestsFinishesOnDispose() throws Exception {
    try {
      Request.Get(format("http://localhost:%s/%s", port.getValue(), "test"))
          .connectTimeout(TIMEOUT).socketTimeout(TIMEOUT).execute();
      fail();
    } catch (SocketTimeoutException ste) {
      // Expected
    }
    server.stop();

    Future<?> disposeTask = newSingleThreadExecutor().submit(() -> {
      server.dispose();
      server = null;
    });

    requestLatch.countDown();
    disposeTask.get(TIMEOUT, MILLISECONDS);
    responseLatch.await(TIMEOUT, MILLISECONDS);
  }

}
