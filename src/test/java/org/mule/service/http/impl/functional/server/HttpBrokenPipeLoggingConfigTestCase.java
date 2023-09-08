/*
 * Copyright 2023 Salesforce, Inc. All rights reserved.
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
import static org.mule.runtime.http.api.domain.message.response.HttpResponse.builder;
import static org.mule.service.http.impl.service.server.grizzly.GrizzlyHttpServer.refreshSystemProperties;
import static org.mule.tck.junit4.AbstractMuleContextTestCase.LOCK_TIMEOUT;

import static java.lang.String.format;
import static java.lang.Thread.currentThread;
import static java.lang.Thread.interrupted;
import static java.util.Collections.singletonList;

import static com.github.valfirst.slf4jtest.TestLoggerFactory.getTestLogger;
import static org.apache.http.client.fluent.Request.Post;
import static org.apache.http.entity.ContentType.DEFAULT_TEXT;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.collection.IsIterableWithSize.iterableWithSize;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;

import org.mule.runtime.core.internal.util.CompositeClassLoader;
import org.mule.runtime.http.api.domain.entity.InputStreamHttpEntity;
import org.mule.runtime.http.api.domain.message.response.HttpResponse;
import org.mule.runtime.http.api.domain.request.HttpRequestContext;
import org.mule.runtime.http.api.server.HttpServer;
import org.mule.runtime.http.api.server.RequestHandler;
import org.mule.runtime.http.api.server.async.HttpResponseReadyCallback;
import org.mule.runtime.http.api.server.async.ResponseStatusCallback;
import org.mule.runtime.http.api.utils.RequestMatcherRegistry;
import org.mule.service.http.impl.service.server.HttpListenerRegistry;
import org.mule.service.http.impl.service.server.HttpServerDelegate;
import org.mule.service.http.impl.service.server.NoListenerRequestHandler;
import org.mule.service.http.impl.service.server.grizzly.BaseResponseCompletionHandler;
import org.mule.service.http.impl.service.server.grizzly.GrizzlyHttpServer;
import org.mule.tck.probe.JUnitLambdaProbe;
import org.mule.tck.probe.PollingProber;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.Writer;
import java.lang.reflect.Field;
import java.net.SocketTimeoutException;
import java.nio.charset.Charset;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

import com.github.valfirst.slf4jtest.TestLogger;

import org.junit.Before;
import org.junit.Test;

import io.qameta.allure.Description;

public class HttpBrokenPipeLoggingConfigTestCase extends AbstractHttpServerTestCase {

  private CountDownLatch requestLatch;
  private CountDownLatch responseLatch;
  private ClassLoader requestHandlerClassLoader;
  private RequestMatcherRegistry<RequestHandler> serverAddressRequestHandlerRegistry;

  private final TestLogger testLogger = getTestLogger(BaseResponseCompletionHandler.class);

  public HttpBrokenPipeLoggingConfigTestCase(String serviceToLoad) {
    super(serviceToLoad);
  }

  @Before
  public void setUp() throws Exception {
    refreshSystemProperties();

    requestLatch = new CountDownLatch(1);
    responseLatch = new CountDownLatch(1);

    setUpServer();

    // Changing the classloader in order to imitate the app's behaviour when handling the error
    Thread currentThread = currentThread();
    ClassLoader originalClassLoader = currentThread.getContextClassLoader();
    requestHandlerClassLoader = CompositeClassLoader.from(currentThread().getContextClassLoader());
    setContextClassLoader(currentThread, originalClassLoader, requestHandlerClassLoader);
    try {
      server.addRequestHandler(singletonList(POST.name()), "/brokenPipe", new ServerErrorResponseHandler());
    } finally {
      setContextClassLoader(currentThread, requestHandlerClassLoader, originalClassLoader);
    }
    createRequestMatcherRegistrySpy();
  }

  @Override
  protected String getServerName() {
    return "broken-pipe-logging-test";
  }

  @Test
  @Description("Verifies that HTTP broken pipe errors are logged in the app log for an app endpoint by checking the TCCL when the log was created")
  public void brokenPipeErrorOnAppEndpointShouldBeLoggedOnTheAppLog() throws Exception {
    sendRequest("brokenPipe");

    responseLatch.await();

    new PollingProber(LOCK_TIMEOUT, 100).check(new JUnitLambdaProbe(() -> {
      assertThat(testLogger.getAllLoggingEvents(), iterableWithSize(1));
      assertThat(testLogger.getAllLoggingEvents().get(0).getThreadContextClassLoader(), is(requestHandlerClassLoader));
      return true;
    }));
  }

  @Test
  @Description("Verifies that HTTP broken pipe errors are logged in the runtime log for unmapped paths by checking the TCCL when the log was created")
  public void brokenPipeErrorOnUnmappedEndpointShouldBeLoggedOnTheRuntimeLog() throws Exception {
    doReturn(new NotFoundResponseHandler()).when(serverAddressRequestHandlerRegistry).find(any());

    sendRequest("unmapped");

    responseLatch.await();

    new PollingProber(LOCK_TIMEOUT, 100).check(new JUnitLambdaProbe(() -> {
      assertThat(testLogger.getAllLoggingEvents(), iterableWithSize(1));
      assertThat(testLogger.getAllLoggingEvents().get(0).getThreadContextClassLoader(),
                 is(currentThread().getContextClassLoader()));
      return true;
    }));
  }

  private void createRequestMatcherRegistrySpy() throws NoSuchFieldException, IllegalAccessException {
    Field listenerRegistryField = GrizzlyHttpServer.class.getDeclaredField("listenerRegistry");
    listenerRegistryField.setAccessible(true);
    HttpServer serverDelegate = ((HttpServerDelegate) server).getDelegate();
    HttpListenerRegistry httpListenerRegistry = (HttpListenerRegistry) listenerRegistryField.get(serverDelegate);
    Field requestHandlerPerServerAddressField = HttpListenerRegistry.class.getDeclaredField("requestHandlerPerServerAddress");
    requestHandlerPerServerAddressField.setAccessible(true);
    Map<HttpServer, RequestMatcherRegistry<RequestHandler>> requestHandlerPerServerAddress =
        (Map<HttpServer, RequestMatcherRegistry<RequestHandler>>) requestHandlerPerServerAddressField.get(httpListenerRegistry);
    serverAddressRequestHandlerRegistry = requestHandlerPerServerAddress.get(serverDelegate);
    serverAddressRequestHandlerRegistry = spy(serverAddressRequestHandlerRegistry);
    requestHandlerPerServerAddress.put(serverDelegate, serverAddressRequestHandlerRegistry);
  }

  private void sendRequest(String endpoint) throws IOException {
    try {
      Post(format("http://localhost:%s/%s", port.getValue(), endpoint))
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
  }

  private class BrokenPipeCausingHandler implements RequestHandler {


    @Override
    public void handleRequest(HttpRequestContext requestContext, HttpResponseReadyCallback responseCallback) {
      try {
        requestLatch.await();
      } catch (InterruptedException e) {
        // Nothing to do
        interrupted();
      }
    }
  }

  private class ServerErrorResponseHandler extends BrokenPipeCausingHandler {

    @Override
    public void handleRequest(HttpRequestContext requestContext, HttpResponseReadyCallback responseCallback) {
      super.handleRequest(requestContext, responseCallback);

      responseCallback.responseReady(builder().statusCode(INTERNAL_SERVER_ERROR.getStatusCode())
          .entity(new InputStreamHttpEntity(new ByteArrayInputStream("test".getBytes())))
          .addHeader(CONTENT_TYPE, TEXT.toRfcString())
          .build(), getCountDownLatchResponseStatusCallbackWrapper(null));
    }
  }

  private class NotFoundResponseHandler extends BrokenPipeCausingHandler {

    @Override
    public void handleRequest(HttpRequestContext requestContext, HttpResponseReadyCallback responseCallback) {
      super.handleRequest(requestContext, responseCallback);

      NoListenerRequestHandler.getInstance().handleRequest(requestContext,
                                                           getCountDownLatchHttpResponseReadyCallbackWrapper(responseCallback));
    }
  }

  private ResponseStatusCallback getCountDownLatchResponseStatusCallbackWrapper(ResponseStatusCallback responseStatusCallback) {
    return new ResponseStatusCallback() {

      @Override
      public void responseSendFailure(Throwable throwable) {
        if (responseStatusCallback != null) {
          responseStatusCallback.responseSendFailure(throwable);
        }
        responseLatch.countDown();
      }

      @Override
      public void responseSendSuccessfully() {
        if (responseStatusCallback != null) {
          responseStatusCallback.responseSendSuccessfully();
        }
        responseLatch.countDown();
      }

      @Override
      public void onErrorSendingResponse(Throwable throwable) {
        if (responseStatusCallback != null) {
          responseStatusCallback.onErrorSendingResponse(throwable);
        }
        responseLatch.countDown();
      }
    };
  }


  private HttpResponseReadyCallback getCountDownLatchHttpResponseReadyCallbackWrapper(HttpResponseReadyCallback responseCallback) {
    return new HttpResponseReadyCallback() {

      @Override
      public void responseReady(HttpResponse response, ResponseStatusCallback responseStatusCallback) {
        responseCallback.responseReady(response, getCountDownLatchResponseStatusCallbackWrapper(responseStatusCallback));
      }

      @Override
      public Writer startResponse(HttpResponse response, ResponseStatusCallback responseStatusCallback, Charset encoding) {
        return responseCallback.startResponse(response, getCountDownLatchResponseStatusCallbackWrapper(responseStatusCallback),
                                              encoding);
      }
    };
  }
}
