/*
 * Copyright 2023 Salesforce, Inc. All rights reserved.
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.service.http.impl.functional.client;

import static org.mule.runtime.api.util.DataUnit.KB;
import static org.mule.service.http.impl.AllureConstants.HttpFeature.HttpStory.STREAMING;

import static java.util.concurrent.TimeUnit.MILLISECONDS;

import static org.slf4j.LoggerFactory.getLogger;

import org.mule.runtime.api.util.concurrent.Latch;
import org.mule.runtime.http.api.client.HttpClient;
import org.mule.runtime.http.api.client.HttpClientConfiguration;
import org.mule.runtime.http.api.client.HttpRequestOptions;
import org.mule.runtime.http.api.domain.message.request.HttpRequest;
import org.mule.runtime.http.api.domain.message.response.HttpResponse;
import org.mule.service.http.impl.service.client.GrizzlyHttpClient;
import org.mule.tck.probe.PollingProber;
import org.mule.tck.probe.Probe;

import java.io.IOException;
import java.lang.reflect.Field;

import org.junit.Test;
import org.junit.jupiter.api.DisplayName;
import org.slf4j.Logger;

import io.qameta.allure.Description;
import io.qameta.allure.Story;

@Story(STREAMING)
@DisplayName("Validates cases in streaming where POST bodies are consumed more than once")
public abstract class HttpClientPostStreamingTestCase extends AbstractHttpClientTestCase {

  private static final Logger LOGGER = getLogger(HttpClientPostStreamingTestCase.class);

  public static final int RESPONSE_TIMEOUT = 3000;
  private static final int TIMEOUT_MILLIS = 1000;
  private static final int POLL_DELAY_MILLIS = 200;

  private String payloadAfterDancing;

  private HttpClientConfiguration.Builder clientBuilder = new HttpClientConfiguration.Builder().setName("streaming-test");
  private PollingProber pollingProber = new PollingProber(TIMEOUT_MILLIS, POLL_DELAY_MILLIS);

  public HttpClientPostStreamingTestCase(String serviceToLoad) {
    super(serviceToLoad);
  }

  @Test
  @Description("Verifies that in streaming the redirection preserves the post body and the request stream is not consumed on redirect without reset")
  public void redirectionPreservesPostBody() throws Exception {
    HttpClient client =
        service.getClientFactory().create(clientBuilder.setResponseBufferSize(KB.toBytes(10)).setStreaming(true).build());
    client.start();
    try {

      HttpRequestOptions options = getOptions();

      HttpRequest request = getRequest();

      Latch responseReceivedLatch = new Latch();
      client.sendAsync(request, options).whenComplete((response, exception) -> responseReceivedLatch.release());
      responseReceivedLatch.await(RESPONSE_TIMEOUT, MILLISECONDS);
      pollingProber.check(new Probe() {

        @Override
        public boolean isSatisfied() {
          return payloadAfterDancing != null && payloadAfterDancing.equals(expectedPayload());
        }

        @Override
        public String describeFailure() {
          return "Payload in post request was not preserved";
        }
      });
    } finally {
      client.stop();
    }
  }

  protected String expectedPayload() {
    return TEST_PAYLOAD;
  }


  public abstract HttpRequest getRequest();

  public abstract HttpRequestOptions getOptions();

  @Override
  protected HttpResponse setUpHttpResponse(HttpRequest request) {
    return doSetUpHttpResponse(request);
  }

  public abstract HttpResponse doSetUpHttpResponse(HttpRequest request);


  protected void extractPayload(HttpRequest request) {
    try {
      payloadAfterDancing = new String(request.getEntity().getBytes());
    } catch (IOException e) {
      LOGGER.debug("Could not extract payload.");
    }
  }

  public static void setRequestStreaming(boolean requestStreaming) throws Exception {
    Field requestStreamingEnabledField = GrizzlyHttpClient.class.getDeclaredField("requestStreamingEnabled");
    requestStreamingEnabledField.setAccessible(true);
    requestStreamingEnabledField.setBoolean(null, requestStreaming);
  }

}
