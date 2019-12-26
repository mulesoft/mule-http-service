/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.service.http.impl.functional.client;

import com.google.common.collect.Maps;
import io.qameta.allure.Description;
import io.qameta.allure.Story;
import io.qameta.allure.junit4.DisplayName;
import org.junit.Before;
import org.junit.Test;
import org.mule.runtime.api.scheduler.SchedulerConfig;
import org.mule.runtime.api.util.Reference;
import org.mule.runtime.api.util.concurrent.Latch;
import org.mule.runtime.core.api.util.IOUtils;
import org.mule.runtime.core.api.util.UUID;
import org.mule.runtime.core.api.util.func.CheckedSupplier;
import org.mule.runtime.http.api.client.HttpClient;
import org.mule.runtime.http.api.client.HttpClientConfiguration;
import org.mule.runtime.http.api.client.HttpRequestOptions;
import org.mule.runtime.http.api.domain.entity.InputStreamHttpEntity;
import org.mule.runtime.http.api.domain.message.request.HttpRequest;
import org.mule.runtime.http.api.domain.message.response.HttpResponse;
import org.mule.service.http.impl.functional.FillAndWaitStream;
import org.mule.service.http.impl.functional.ResponseReceivedProbe;
import org.mule.tck.probe.PollingProber;
import org.mule.tck.probe.Probe;
import org.mule.tck.probe.Prober;
import org.slf4j.MDC;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ExecutorService;

import static java.util.concurrent.Executors.newSingleThreadExecutor;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.isA;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertThat;
import static org.mule.runtime.api.util.DataUnit.KB;
import static org.mule.runtime.http.api.HttpConstants.HttpStatus.OK;
import static org.mule.service.http.impl.AllureConstants.HttpFeature.HttpStory.STREAMING;
import static org.mule.service.http.impl.functional.FillAndWaitStream.RESPONSE_SIZE;

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
    HttpClient client =
        service.getClientFactory().create(clientBuilder.setResponseBufferSize(KB.toBytes(10)).setStreaming(true).build());
    client.start();
    final Reference<HttpResponse> responseReference = new Reference<>();
    try {
      client.sendAsync(getRequest(), getDefaultOptions(RESPONSE_TIMEOUT)).whenComplete(
                                                                                       (response, exception) -> responseReference
                                                                                           .set(response));
      pollingProber.check(new ResponseReceivedProbe(responseReference));
      verifyStreamed(responseReference.get());
    } finally {
      client.stop();
    }
  }


  @Test
  @Description("Uses a streaming HTTP client to send a non blocking request and asserts that MDC values are propagated to the response handler when the request fails due to timeout.")
  public void nonBlockingStreamingMDCPropagationOnError() throws Exception {
    testMdcPropagation(true, true);
  }

  @Test
  @Description("Uses a non streaming HTTP client to send a non blocking request and asserts that MDC values are propagated to the response handler when the request fails due to timeout.")
  public void nonBlockingNoStreamingMDCPropagationOnError() throws Exception {
    testMdcPropagation(false, true);
  }

  @Test
  @Description("Uses a streaming HTTP client to send a non blocking request and asserts that MDC values are propagated to the response handler when the request is executed successfully.")
  public void nonBlockingStreamingMDCPropagationNoError() throws Exception {
    testMdcPropagation(true, false);
  }

  @Test
  @Description("Uses a non streaming HTTP client to send a non blocking request and asserts that MDC values are propagated to the response handler when the request is executed successfully.")
  public void nonBlockingNoStreamingMDCPropagationNoError() throws Exception {
    testMdcPropagation(false, false);
  }

  @Test
  @Description("Uses a non streaming HTTP client to send a non blocking request which will not finish until the stream is released.")
  public void nonBlockingMemory() throws Exception {
    HttpClient client = service.getClientFactory().create(clientBuilder.setStreaming(false).build());
    client.start();
    final Reference<HttpResponse> responseReference = new Reference<>();
    try {
      client.sendAsync(getRequest(), getDefaultOptions(RESPONSE_TIMEOUT)).whenComplete(
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
    HttpClient client = service.getClientFactory().create(clientBuilder.setStreaming(true).build());
    client.start();
    try {
      HttpResponse response = client.send(getRequest(), getDefaultOptions(RESPONSE_TIMEOUT));
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
          responseReference.set(client.send(getRequest(), getDefaultOptions(RESPONSE_TIMEOUT)));
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

  private HttpRequest getRequest(String uri) {
    return HttpRequest.builder().uri(uri).build();
  }

  private HttpRequest getRequest() {
    return getRequest(getUri());
  }

  private void verifyStreamed(HttpResponse response) throws IOException {
    assertThat(response.getStatusCode(), is(OK.getStatusCode()));
    latch.release();
    verifyBody(response);
  }

  private void verifyNotStreamed(Reference<HttpResponse> responseReference) throws Exception {
    // Allow the request/response process to start
    Thread.sleep(1000);
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

  protected HttpRequestOptions getDefaultOptions(int responseTimeout) {
    return HttpRequestOptions.builder().responseTimeout(responseTimeout).build();
  }

  private void testMdcPropagation(boolean shouldStream, boolean shouldThrowException) throws IOException {
    String transactionId = UUID.getUUID();
    Map<String, Object> capture = Maps.newHashMap();
    MDC.put("transactionId", transactionId);
    MDC.put("currentThread", Thread.currentThread().getName());
    HttpClient client =
        service.getClientFactory(SchedulerConfig.config().withName("test-scheduler").withMaxConcurrentTasks(5))
            .create(clientBuilder.setResponseBufferSize(KB.toBytes(10)).setStreaming(shouldStream).build());
    client.start();
    final Reference<HttpResponse> responseReference = new Reference<>();
    if (!shouldThrowException) {
      // Release the lock on the streaming payload in order to finish without throwing an exception.
      latch.release();
    }
    try {
      client.sendAsync(shouldThrowException ? getRequest("http://localhost:9999") : getRequest(),
                       getDefaultOptions(RESPONSE_TIMEOUT))
          .whenComplete(
                        (response, exception) -> {
                          if (shouldThrowException) {
                            responseReference.set(HttpResponse.builder().statusCode(500).build());
                          } else {
                            responseReference.set(response);
                          }
                          // since assertions won't fail here we need to capture this variables to assert later on.
                          capture.put("exception", exception);
                          capture.put("transactionId",
                                      MDC.get("transactionId"));
                          capture.put("currentThread", Thread
                              .currentThread().getName());
                        });

      pollingProber.check(new ResponseReceivedProbe(responseReference));
      assertThat(capture.get("exception"), shouldThrowException ? notNullValue() : nullValue());
      assertThat(MDC.get("transactionId"), is(capture.get("transactionId")));
      assertThat(MDC.get("currentThread"), is(not(capture.get("currentThread"))));
      assertThat(responseReference.get().getStatusCode(), shouldThrowException ? is(500) : is(200));
    } finally {
      client.stop();
    }
  }

}
