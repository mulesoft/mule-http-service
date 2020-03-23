/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.service.http.impl.functional.client;

import static java.lang.Thread.currentThread;
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
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.Before;
import org.junit.Test;
import org.mule.runtime.api.util.Reference;
import org.mule.runtime.api.util.concurrent.Latch;
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
import org.mule.tck.probe.JUnitProbe;
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

  public HttpClientStreamingClassLoaderTestCase(String serviceToLoad) {
    super(serviceToLoad);
  }

  @Before
  public void setupClassloader() {
    this.classLoader = new ClassLoader() {};
  }

  @Override
  protected HttpResponse setUpHttpResponse(HttpRequest request) {
    return HttpResponse.builder()
        .statusCode(OK.getStatusCode())
        .reasonPhrase(OK.getReasonPhrase())
        .entity(new InputStreamHttpEntity(new FillAndWaitStream(latch) {

          @Override
          public int read() throws IOException {
            assertThat(currentThread().getContextClassLoader(), is(classLoader));
            return super.read();
          }
        }))
        .build();
  }

  @Test
  @Description("ContextClassLoader when reading should be the same that the RequestHandler's one")
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
    } finally {
      client.stop();
    }
  }

  @Test
  @Description("ContextClassLoader when a request fails should be the same that the RequestHandler's one")
  @Issue("MULE-18185")
  public void properClassLoaderWhenFails() throws Exception {
    AtomicBoolean sameClassloader = new AtomicBoolean(false);

    doAnswer(i -> {
      sameClassloader.set(currentThread().getContextClassLoader() == classLoader);
      return null;
    }).when(statusCallback).onErrorSendingResponse(any());


    Socket socket = new Socket("localhost", port.getNumber());
    sendRequest(socket);
    socket.close();
    latch.release();

    new PollingProber(2000, 200).check(new JUnitProbe() {

      @Override
      protected boolean test() {
        verify(statusCallback, atLeastOnce()).onErrorSendingResponse(any());
        return true;
      }

    });

    assertThat(sameClassloader.get(), is(true));
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
