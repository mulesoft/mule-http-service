/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.service.http.impl.functional.client;

import static java.util.concurrent.Executors.newSingleThreadExecutor;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertThat;
import static org.mule.runtime.api.util.DataUnit.KB;
import static org.mule.runtime.http.api.HttpConstants.HttpStatus.OK;
import static org.mule.service.http.impl.AllureConstants.HttpFeature.HttpStory.STREAMING;
import static org.mule.service.http.impl.functional.FillAndWaitStream.RESPONSE_SIZE;
import org.mule.runtime.api.util.Reference;
import org.mule.runtime.core.api.util.IOUtils;
import org.mule.runtime.api.util.concurrent.Latch;
import org.mule.runtime.http.api.client.HttpClient;
import org.mule.runtime.http.api.client.HttpClientConfiguration;
import org.mule.runtime.http.api.domain.entity.InputStreamHttpEntity;
import org.mule.runtime.http.api.domain.message.request.HttpRequest;
import org.mule.runtime.http.api.domain.message.response.HttpResponse;
import org.mule.service.http.impl.functional.FillAndWaitStream;
import org.mule.tck.probe.PollingProber;
import org.mule.tck.probe.Probe;

import java.io.IOException;
import java.util.concurrent.ExecutorService;

import io.qameta.allure.Description;
import io.qameta.allure.Story;
import io.qameta.allure.junit4.DisplayName;
import org.junit.Before;
import org.junit.Test;

@Story(STREAMING)
@DisplayName("Validates HTTP client behaviour against a streaming server.")
public class HttpClientStreamingTestCase extends AbstractHttpClientTestCase {

  private static final int RESPONSE_TIMEOUT = 3000;
  private static final int TIMEOUT_MILLIS = 1000;
  private static final int POLL_DELAY_MILLIS = 200;

  private static Latch latch;

  private HttpClientConfiguration.Builder clientBuilder = new HttpClientConfiguration.Builder().setName("streaming-test");
  private PollingProber pollingProber = new PollingProber(TIMEOUT_MILLIS, POLL_DELAY_MILLIS);

  public HttpClientStreamingTestCase(String serviceToLoad) {
    super(serviceToLoad);
  }

  @Before
  public void createLatch() {
    latch = new Latch();
  }

  @Test
  @Description("Uses a streaming HTTP client to send a non blocking request which will finish before the stream is released.")
  public void nonBlockingStreaming() throws Exception {
    HttpClient client = service.getClientFactory().create(clientBuilder.setResponseBufferSize(KB.toBytes(10)).build());
    client.start();
    final Reference<HttpResponse> responseReference = new Reference<>();
    try {
      client.sendAsync(getRequest(), RESPONSE_TIMEOUT, true, null).whenComplete(
                                                                                (response, exception) -> responseReference
                                                                                    .set(response));
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
      client.sendAsync(getRequest(), RESPONSE_TIMEOUT, true, null).whenComplete(
                                                                                (response, exception) -> responseReference
                                                                                    .set(response));
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
    return HttpRequest.builder().uri(getUri()).build();
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

  @Override
  protected HttpResponse setUpHttpResponse(HttpRequest request) {
    return HttpResponse.builder()
        .statusCode(OK.getStatusCode())
        .reasonPhrase(OK.getReasonPhrase())
        .entity(new InputStreamHttpEntity(new FillAndWaitStream(latch)))
        .build();
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
