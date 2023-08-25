/*
 * Copyright 2023 Salesforce, Inc. All rights reserved.
 */
package org.mule.service.http.impl.functional.client;

import static java.lang.Thread.currentThread;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mule.runtime.api.util.DataUnit.KB;
import static org.mule.runtime.http.api.HttpConstants.HttpStatus.OK;
import static org.mule.service.http.impl.AllureConstants.HttpFeature.HttpStory.STREAMING;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.Before;
import org.junit.Test;
import org.junit.runners.Parameterized;
import org.mule.runtime.api.util.Reference;
import org.mule.runtime.api.util.concurrent.Latch;
import org.mule.runtime.extension.api.annotation.param.Parameter;
import org.mule.runtime.http.api.client.HttpClient;
import org.mule.runtime.http.api.client.HttpClientConfiguration.Builder;
import org.mule.runtime.http.api.domain.entity.InputStreamHttpEntity;
import org.mule.runtime.http.api.domain.message.request.HttpRequest;
import org.mule.runtime.http.api.domain.message.response.HttpResponse;
import org.mule.runtime.http.api.domain.request.HttpRequestContext;
import org.mule.runtime.http.api.server.RequestHandler;
import org.mule.runtime.http.api.server.async.HttpResponseReadyCallback;
import org.mule.runtime.http.api.server.async.ResponseStatusCallback;
import org.mule.service.http.impl.functional.FillAndWaitStream;
import org.mule.service.http.impl.functional.ResponseReceivedProbe;
import org.mule.service.http.impl.service.HttpServiceImplementation;
import org.mule.service.http.impl.service.server.grizzly.GrizzlyHttpServer;
import org.mule.service.http.impl.service.server.grizzly.ResponseStreamingCompletionHandler;
import org.mule.tck.probe.PollingProber;

import io.qameta.allure.Description;
import io.qameta.allure.Issue;
import io.qameta.allure.Story;
import io.qameta.allure.junit4.DisplayName;

@Story(STREAMING)
@DisplayName("Validates ClassLoader manipulation when using the HTTP client against a streaming server.")
public class HttpClientStreamingClassLoaderTestCase extends AbstractHttpClientTestCase {

  private static final int RESPONSE_TIMEOUT = 30000;
  private static final int TIMEOUT_MILLIS = 30000;
  private static final int POLL_DELAY_MILLIS = 200;

  private PollingProber pollingProber = new PollingProber(TIMEOUT_MILLIS, POLL_DELAY_MILLIS);
  private ResponseStatusCallback statusCallback = spy(ResponseStatusCallback.class);

  private Latch latch = new Latch();
  private ClassLoader classLoader;

  private Set<ClassLoader> classLoadersWhileReading;

  @Parameter
  private final boolean replaceCtxClassLoader;

  @Parameterized.Parameters(name = "Service: {0}, replaceCtxClassLoader: {1}")
  public static Object[][] parameters() {
    return new Object[][] {
        {HttpServiceImplementation.class.getName(), true},
        {HttpServiceImplementation.class.getName(), false}
    };
  }

  public HttpClientStreamingClassLoaderTestCase(String serviceToLoad, boolean replaceCtxClassLoader) {
    super(serviceToLoad);
    this.replaceCtxClassLoader = replaceCtxClassLoader;

    // noinspection deprecation
    ResponseStreamingCompletionHandler.setReplaceCtxClassloader(replaceCtxClassLoader);
    // noinspection deprecation
    GrizzlyHttpServer.setReplaceCtxClassloader(replaceCtxClassLoader);

    this.classLoader = new ClassLoader() {};
    this.classLoadersWhileReading = new HashSet<>();
  }

  @Override
  protected HttpResponse setUpHttpResponse(HttpRequest request) {
    return HttpResponse.builder()
        .statusCode(OK.getStatusCode())
        .reasonPhrase(OK.getReasonPhrase())
        .entity(new InputStreamHttpEntity(new FillAndWaitStream(latch) {

          @Override
          public int read() throws IOException {
            classLoadersWhileReading.add(currentThread().getContextClassLoader());
            return super.read();
          }
        }))
        .build();
  }

  @Test
  @Description("ContextClassLoader when reading should be as expected, depending on parametrization")
  @Issue("MULE-18185")
  public void properClassloaderWhileReading() {
    Builder clientBuilder = new Builder().setName("streaming-classloading-test");

    HttpClient client = service.getClientFactory()
        .create(clientBuilder.setResponseBufferSize(KB.toBytes(10)).setStreaming(false).build());

    client.start();
    final Reference<HttpResponse> responseReference = new Reference<>();
    try {
      client.sendAsync(getRequest(), getDefaultOptions(RESPONSE_TIMEOUT)).whenComplete((response, exception) -> {
        assertThat(response, is(not(nullValue())));
        assertThat(exception, is(nullValue()));

        responseReference.set(response);
      });

      pollingProber.check(new ResponseReceivedProbe(responseReference));
      long differentClassLoadersCount = classLoadersWhileReading.stream().filter(cl -> cl != classLoader).count();
      if (replaceCtxClassLoader) {
        assertThat(differentClassLoadersCount, is(0L));
      } else {
        assertThat(differentClassLoadersCount, is(1L));
      }
    } finally {
      client.stop();
    }
  }

  @Test
  @Description("ContextClassLoader when a request fails should be as expected depending on parametrization")
  @Issue("MULE-18185")
  public void properClassLoaderWhenFails() throws Exception {
    AtomicBoolean sameClassloader = new AtomicBoolean(false);

    doAnswer(i -> {
      sameClassloader.set(currentThread().getContextClassLoader() == classLoader);
      latch.release();
      return null;
    }).when(statusCallback).onErrorSendingResponse(any());


    Socket socket = new Socket("localhost", port.getNumber());
    sendRequest(socket);
    socket.close();

    latch.await(TIMEOUT, MILLISECONDS);

    verify(statusCallback, atLeastOnce()).onErrorSendingResponse(any());
    if (replaceCtxClassLoader) {
      assertThat(sameClassloader.get(), is(true));
    } else {
      assertThat(sameClassloader.get(), is(not(true)));
    }
  }

  private void sendRequest(Socket socket) throws IOException {
    PrintWriter writer = new PrintWriter(socket.getOutputStream());
    writer.println("GET /test HTTP/1.1");
    writer.println("Host: www.example.com");
    writer.println("");
    writer.flush();
  }

  private HttpRequest getRequest(String uri) {
    return HttpRequest.builder().uri(uri).build();
  }

  private HttpRequest getRequest() {
    return getRequest(getUri());
  }

  protected RequestHandler getRequestHandler() {
    return new RequestHandler() {

      @Override
      public void handleRequest(HttpRequestContext requestContext, HttpResponseReadyCallback responseCallback) {
        responseCallback.responseReady(setUpHttpResponse(requestContext.getRequest()), statusCallback);
      }

      @Override
      public ClassLoader getContextClassLoader() {
        return classLoader;
      }
    };
  }
}
