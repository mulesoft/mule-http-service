/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.service.http.impl.functional.server;

import static org.mule.runtime.api.metadata.MediaType.TEXT;
import static org.mule.runtime.core.api.util.ClassUtils.setContextClassLoader;
import static org.mule.runtime.http.api.HttpConstants.HttpStatus.INTERNAL_SERVER_ERROR;
import static org.mule.runtime.http.api.HttpConstants.Method.POST;
import static org.mule.runtime.http.api.HttpHeaders.Names.CONNECTION;
import static org.mule.runtime.http.api.HttpHeaders.Names.CONTENT_TYPE;
import static org.mule.runtime.http.api.HttpHeaders.Values.CLOSE;

import static java.lang.String.format;
import static java.lang.Thread.currentThread;
import static java.util.Collections.singletonList;

import static com.github.valfirst.slf4jtest.TestLoggerFactory.getTestLogger;
import static org.apache.http.entity.ContentType.DEFAULT_TEXT;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

import org.mule.runtime.core.internal.util.CompositeClassLoader;
import org.mule.runtime.http.api.domain.entity.InputStreamHttpEntity;
import org.mule.runtime.http.api.domain.message.response.HttpResponse;
import org.mule.runtime.http.api.domain.request.HttpRequestContext;
import org.mule.runtime.http.api.server.RequestHandler;
import org.mule.runtime.http.api.server.async.HttpResponseReadyCallback;
import org.mule.runtime.http.api.server.async.ResponseStatusCallback;
import org.mule.service.http.impl.service.server.grizzly.BaseResponseCompletionHandler;
import org.mule.tck.probe.JUnitLambdaProbe;
import org.mule.tck.probe.PollingProber;

import java.io.ByteArrayInputStream;
import java.net.SocketTimeoutException;
import java.util.concurrent.CountDownLatch;

import com.github.valfirst.slf4jtest.TestLogger;
import io.qameta.allure.Description;
import org.apache.http.client.fluent.Request;
import org.junit.Before;
import org.junit.Test;

public class HttpBrokenPipeLoggingConfigTestCase extends AbstractHttpServerTestCase {

  private CountDownLatch requestLatch;
  private CountDownLatch responseLatch;
  private ClassLoader requestHandlerClassLoader;

  TestLogger testLogger = getTestLogger(BaseResponseCompletionHandler.class);

  public HttpBrokenPipeLoggingConfigTestCase(String serviceToLoad) {
    super(serviceToLoad);
  }

  @Before
  public void setUp() throws Exception {
    requestLatch = new CountDownLatch(1);
    responseLatch = new CountDownLatch(1);

    setUpServer();

    // Changing the classloader in order to imitate the app's behaviour when handling the error
    Thread currentThread = currentThread();
    ClassLoader originalClassLoader = currentThread.getContextClassLoader();
    requestHandlerClassLoader = CompositeClassLoader.from(currentThread().getContextClassLoader());
    setContextClassLoader(currentThread, originalClassLoader, requestHandlerClassLoader);
    try {
      addRequestHandler();
    } finally {
      setContextClassLoader(currentThread, requestHandlerClassLoader, originalClassLoader);
    }
  }

  @Override
  protected String getServerName() {
    return "broken-pipe-logging-test";
  }

  @Test
  @Description("Verifies that HTTP broken pipe errors are logged in the app log for an app endpoint by checking the TCCL when the log was created")
  public void brokenPipeErrorOnAppEndpointShouldBeLoggedOnTheAppLog() throws Exception {
    try {
      Request.Post(format("http://localhost:%s/%s", port.getValue(), "brokenPipe"))
          .bodyString("That is not dead which can eternal lie, And with strange aeons even death may die.", DEFAULT_TEXT)
          .socketTimeout(1)
          .addHeader(CONNECTION, CLOSE)
          .execute()
          .returnResponse();
      fail();
    } catch (SocketTimeoutException ste) {
      // Expected
      requestLatch.countDown();
    }

    responseLatch.await();

    new PollingProber(getTestTimeoutSecs(), 100).check(new JUnitLambdaProbe(() -> {
      assertThat(testLogger.getAllLoggingEvents().size(), is(1));
      assertThat(testLogger.getAllLoggingEvents().get(0).getThreadContextClassLoader(), is(requestHandlerClassLoader));
      return true;
    }));
  }

  @Test
  @Description("Verifies that HTTP broken pipe errors are logged in the runtime log for unmapped paths by checking the TCCL when the log was created")
  public void brokenPipeErrorOnUnmappedEndpointShouldBeLoggedOnTheRuntimeLog() throws Exception {
    try {
      Request.Post(format("http://localhost:%s/%s", port.getValue(), "unmapped"))
          .bodyString("That is not dead which can eternal lie, And with strange aeons even death may die.", DEFAULT_TEXT)
          .socketTimeout(1)
          .execute()
          .returnResponse();
      fail();
    } catch (SocketTimeoutException ste) {
      // Expected
    }

    new PollingProber(getTestTimeoutSecs() * 1000, 100).check(new JUnitLambdaProbe(() -> {
      assertThat(testLogger.getAllLoggingEvents().size(), is(1));
      assertThat(testLogger.getAllLoggingEvents().get(0).getThreadContextClassLoader(),
                 is(currentThread().getContextClassLoader()));
      return true;
    }));
  }

  private void addRequestHandler() {
    server.addRequestHandler(singletonList(POST.name()), "/brokenPipe", new BrokenPipeCausingHandler());
  }

  private class BrokenPipeCausingHandler implements RequestHandler {

    @Override
    public void handleRequest(HttpRequestContext requestContext, HttpResponseReadyCallback responseCallback) {
      try {
        requestLatch.await();
      } catch (InterruptedException e) {
        // Nothing to do
        Thread.interrupted();
      }

      responseCallback.responseReady(HttpResponse.builder().statusCode(INTERNAL_SERVER_ERROR.getStatusCode())
          .entity(new InputStreamHttpEntity(new ByteArrayInputStream("test".getBytes())))
          .addHeader(CONTENT_TYPE, TEXT.toRfcString())
          .build(), getResponseCallback());
    }
  }

  private ResponseStatusCallback getResponseCallback() {
    return new ResponseStatusCallback() {

      @Override
      public void responseSendFailure(Throwable throwable) {
        responseLatch.countDown();
      }

      @Override
      public void responseSendSuccessfully() {
        responseLatch.countDown();
      }

      @Override
      public void onErrorSendingResponse(Throwable throwable) {
        responseLatch.countDown();
      }
    };
  }
}
