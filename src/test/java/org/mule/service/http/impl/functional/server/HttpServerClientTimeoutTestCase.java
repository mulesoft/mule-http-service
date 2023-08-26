/*
 * Copyright 2023 Salesforce, Inc. All rights reserved.
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

import java.net.SocketTimeoutException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;

import org.apache.http.client.fluent.Request;
import org.junit.Before;
import org.junit.Test;

public class HttpServerClientTimeoutTestCase extends AbstractHttpServerTestCase {

  private static final int TIMEOUT = 5000;

  private CountDownLatch requestLatch;
  private CountDownLatch responseLatch;

  public HttpServerClientTimeoutTestCase(String serviceToLoad) {
    super(serviceToLoad);
  }

  @Before
  public void setUp() throws Exception {
    requestLatch = new CountDownLatch(1);
    responseLatch = new CountDownLatch(1);

    setUpServer();
    server.addRequestHandler(singletonList(GET.name()), "/test", (requestContext, responseCallback) -> {

      try {
        requestLatch.await();
      } catch (InterruptedException e) {
        // Nothing to do
      }

      responseCallback.responseReady(HttpResponse.builder().statusCode(INTERNAL_SERVER_ERROR.getStatusCode())
          .entity(new ByteArrayHttpEntity("Success!".getBytes()))
          .addHeader(CONTENT_TYPE, TEXT.toRfcString())
          .build(), new IgnoreResponseStatusCallback() {

            @Override
            public void responseSendSuccessfully() {
              responseLatch.countDown();
            }
          });
    });
  }

  @Override
  protected String getServerName() {
    return "client-timeout-test";
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
