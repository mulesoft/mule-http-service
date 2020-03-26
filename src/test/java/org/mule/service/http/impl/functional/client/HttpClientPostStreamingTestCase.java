/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.service.http.impl.functional.client;

import static org.apache.commons.io.IOUtils.toByteArray;
import static org.mule.runtime.api.util.DataUnit.KB;
import static org.mule.service.http.impl.AllureConstants.HttpFeature.HttpStory.STREAMING;
import static org.slf4j.LoggerFactory.getLogger;

import java.io.IOException;

import org.junit.Test;
import org.mule.runtime.api.util.Reference;
import org.mule.runtime.http.api.client.HttpClient;
import org.mule.runtime.http.api.client.HttpClientConfiguration;
import org.mule.runtime.http.api.client.auth.HttpAuthentication;
import org.mule.runtime.http.api.domain.message.request.HttpRequest;
import org.mule.runtime.http.api.domain.message.response.HttpResponse;
import org.mule.tck.probe.PollingProber;
import org.mule.tck.probe.Probe;
import org.slf4j.Logger;

import io.qameta.allure.Description;
import io.qameta.allure.Story;
import io.qameta.allure.junit4.DisplayName;

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
    final Reference<HttpResponse> responseReference = new Reference<>();
    try {

      HttpRequest request = getRequest();

      client.sendAsync(request, RESPONSE_TIMEOUT, true, getAuthentication()).whenComplete(
                                                                                          (response,
                                                                                           exception) -> responseReference
                                                                                               .set(response));
      pollingProber.check(new Probe() {

        @Override
        public boolean isSatisfied() {
          return responseReference.get() != null;
        }

        @Override
        public String describeFailure() {
          return "Response was not received.";
        }
      });
      pollingProber.check(new Probe() {

        @Override
        public boolean isSatisfied() {
          return payloadAfterDancing != null && payloadAfterDancing.equals(TEST_PAYLOAD);
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


  public abstract HttpRequest getRequest();

  @Override
  protected HttpResponse setUpHttpResponse(HttpRequest request) {
    return doSetUpHttpResponse(request);
  }

  public abstract HttpResponse doSetUpHttpResponse(HttpRequest request);

  public abstract HttpAuthentication getAuthentication();

  protected void extractPayload(HttpRequest request) {
    try {
      payloadAfterDancing = new String(toByteArray(request.getEntity().getContent()));
    } catch (IOException e) {
      LOGGER.debug("Could not extract payload.");
    }
  }

}
