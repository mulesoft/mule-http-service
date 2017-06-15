/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.service.http.impl.service.client;

import static java.lang.String.format;
import static java.util.concurrent.Executors.newSingleThreadExecutor;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertThat;
import static org.mule.runtime.http.api.HttpConstants.HttpStatus.OK;
import static org.mule.service.http.impl.service.AllureConstants.HttpFeature.HTTP_SERVICE;
import static org.mule.service.http.impl.service.AllureConstants.HttpFeature.HttpStory.STREAMING;
import org.mule.runtime.api.util.Reference;
import org.mule.runtime.core.api.util.IOUtils;
import org.mule.runtime.core.api.util.concurrent.Latch;
import org.mule.runtime.http.api.client.HttpClient;
import org.mule.runtime.http.api.client.HttpClientConfiguration;
import org.mule.runtime.http.api.client.async.ResponseHandler;
import org.mule.runtime.http.api.domain.entity.InputStreamHttpEntity;
import org.mule.runtime.http.api.domain.message.request.HttpRequest;
import org.mule.runtime.http.api.domain.message.response.HttpResponse;
import org.mule.runtime.http.api.server.HttpServer;
import org.mule.runtime.http.api.server.HttpServerConfiguration;
import org.mule.runtime.http.api.server.async.ResponseStatusCallback;
import org.mule.service.http.impl.service.HttpServiceImplementation;
import org.mule.tck.SimpleUnitTestSupportSchedulerService;
import org.mule.tck.junit4.AbstractMuleTestCase;
import org.mule.tck.junit4.rule.DynamicPort;
import org.mule.tck.probe.PollingProber;
import org.mule.tck.probe.Probe;

import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.ExecutorService;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import ru.yandex.qatools.allure.annotations.Description;
import ru.yandex.qatools.allure.annotations.Features;
import ru.yandex.qatools.allure.annotations.Stories;

@Features(HTTP_SERVICE)
@Stories(STREAMING)
@Description("Validates HTTP client behaviour against a streaming server.")
public class HttpClientStreamingTestCase extends AbstractMuleTestCase {

  @Rule
  public DynamicPort serverPort = new DynamicPort("serverPort");

  // Use a payload bigger than the default server and client buffer sizes (8 and 10 KB, respectively)
  private static final int RESPONSE_SIZE = 14 * 1024;
  private static final int WAIT_TIMEOUT = 5000;
  private static final int RESPONSE_TIMEOUT = 3000;
  private static final int TIMEOUT_MILLIS = 1000;
  private static final int POLL_DELAY_MILLIS = 200;

  private static Latch latch;

  private HttpServiceImplementation service = new HttpServiceImplementation(new SimpleUnitTestSupportSchedulerService());
  private HttpClientConfiguration.Builder clientBuilder = new HttpClientConfiguration.Builder().setName("streaming-test");
  private PollingProber pollingProber = new PollingProber(TIMEOUT_MILLIS, POLL_DELAY_MILLIS);
  private HttpServer server;

  @Before
  public void setUp() throws Exception {
    latch = new Latch();
    service.start();
    server = service.getServerFactory().create(new HttpServerConfiguration.Builder()
        .setHost("localhost")
        .setPort(serverPort.getNumber())
        .setName("streaming-test")
        .build());
    server.start();
    server.addRequestHandler("/",
                             (requestContext, responseCallback) -> responseCallback.responseReady(setUpHttpResponse(),
                                                                                                  new IgnoreResponseStatusCallback()));
  }

  @After
  public void tearDown() throws Exception {
    if (server != null) {
      server.stop();
      server.dispose();
    }
    service.stop();
  }

  @Test
  @Description("Uses a streaming HTTP client to send a non blocking request which will finish before the stream is released.")
  public void nonBlockingStreaming() throws Exception {
    HttpClient client = service.getClientFactory().create(clientBuilder.build());
    client.start();
    final Reference<HttpResponse> responseReference = new Reference<>();
    try {
      client.send(getRequest(), RESPONSE_TIMEOUT, true, null, new ResponseHandler() {

        @Override
        public void onCompletion(HttpResponse response) {
          responseReference.set(response);
        }

        @Override
        public void onFailure(Exception exception) {
          // Do nothing, probe will fail.
        }
      });
      pollingProber.check(new ResponseReceivedProbe(responseReference));
      verifyStreamed(responseReference.get());
    } finally {
      client.stop();
    }
  }

  @Test
  @Description("Uses a non streaming HTTP client to send a non blocking request which will not finish until the stream is released.")
  public void nonBlockingMemory() throws Exception {
    HttpClient client = service.getClientFactory().create(clientBuilder.setStreaming(false).build());
    client.start();
    final Reference<HttpResponse> responseReference = new Reference<>();
    try {
      client.send(getRequest(), RESPONSE_TIMEOUT, true, null, new ResponseHandler() {

        @Override
        public void onCompletion(HttpResponse response) {
          responseReference.set(response);
        }

        @Override
        public void onFailure(Exception exception) {
          // Do nothing, probe will fail.
        }
      });
      verifyNotStreamed(responseReference);
    } finally {
      client.stop();
    }
  }

  @Test
  @Description("Uses a streaming HTTP client to send a blocking request which will finish before the stream is released.")
  public void blockingStreaming() throws Exception {
    HttpClient client = service.getClientFactory().create(clientBuilder.build());
    client.start();
    try {
      HttpResponse response = client.send(getRequest(), RESPONSE_TIMEOUT, true, null);
      verifyStreamed(response);
    } finally {
      client.stop();
    }
  }

  @Test
  @Description("Uses a non streaming HTTP client to send a request which will not finish until the stream is released.")
  public void blockingMemory() throws Exception {
    HttpClient client = service.getClientFactory().create(clientBuilder.setStreaming(false).build());
    client.start();
    Reference<HttpResponse> responseReference = new Reference<>();
    ExecutorService executorService = newSingleThreadExecutor();
    try {
      executorService.execute(() -> {
        try {
          responseReference.set(client.send(getRequest(), RESPONSE_TIMEOUT, true, null));
        } catch (Exception e) {
          // Do nothing, probe will fail.
        }
      });
      verifyNotStreamed(responseReference);
    } finally {
      executorService.shutdown();
      client.stop();
    }
  }

  private HttpRequest getRequest() {
    return HttpRequest.builder().setUri(getUrl()).build();
  }

  private void verifyStreamed(HttpResponse response) throws IOException {
    assertThat(response.getStatusCode(), is(OK.getStatusCode()));
    latch.release();
    verifyBody(response);
  }

  private void verifyNotStreamed(Reference<HttpResponse> responseReference) throws IOException {
    assertThat(responseReference.get(), is(nullValue()));
    latch.release();
    pollingProber.check(new ResponseReceivedProbe(responseReference));
    assertThat(responseReference.get().getStatusCode(), is(OK.getStatusCode()));
    verifyBody(responseReference.get());
  }

  private void verifyBody(HttpResponse response) throws IOException {
    assertThat(IOUtils.toString(response.getEntity().getContent()).length(), is(RESPONSE_SIZE));
  }

  private String getUrl() {
    return format("http://localhost:%s/", serverPort.getValue());
  }

  private HttpResponse setUpHttpResponse() {
    return HttpResponse.builder()
        .setStatusCode(OK.getStatusCode())
        .setReasonPhrase(OK.getReasonPhrase())
        .setEntity(new InputStreamHttpEntity(new FillAndWaitStream()))
        .build();
  }

  /**
   * Custom {@link InputStream} that will fill the internal buffers and over to a specific size, then wait before completing.
   */
  private class FillAndWaitStream extends InputStream {

    private int sent = 0;

    @Override
    public int read() throws IOException {
      if (sent < RESPONSE_SIZE) {
        sent++;
        return 42;
      } else {
        try {
          latch.await(WAIT_TIMEOUT, MILLISECONDS);
        } catch (InterruptedException e) {
          // Do nothing
        }
        return -1;
      }
    }
  }

  private class IgnoreResponseStatusCallback implements ResponseStatusCallback {

    @Override
    public void responseSendFailure(Throwable throwable) {
      // Do nothing
    }

    @Override
    public void responseSendSuccessfully() {
      // Do nothing
    }
  }

  private class ResponseReceivedProbe implements Probe {

    private Reference<HttpResponse> responseReference;

    public ResponseReceivedProbe(Reference<HttpResponse> responseReference) {
      this.responseReference = responseReference;
    }

    @Override
    public boolean isSatisfied() {
      return responseReference.get() != null;
    }

    @Override
    public String describeFailure() {
      return "Response was not received.";
    }
  }

}
