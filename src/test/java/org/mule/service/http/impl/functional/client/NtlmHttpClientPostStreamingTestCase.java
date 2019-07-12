/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.service.http.impl.functional.client;

import static org.mule.runtime.http.api.HttpConstants.Method.POST;
import static org.mule.service.http.impl.AllureConstants.HttpFeature.HttpStory.STREAMING;
import org.mule.runtime.http.api.client.HttpRequestOptions;
import org.mule.runtime.http.api.client.auth.HttpAuthentication;
import org.mule.runtime.http.api.client.auth.HttpAuthentication.HttpNtlmAuthentication;
import org.mule.runtime.http.api.domain.entity.InputStreamHttpEntity;
import org.mule.runtime.http.api.domain.message.request.HttpRequest;
import org.mule.runtime.http.api.domain.message.response.HttpResponse;
import org.mule.runtime.http.api.domain.message.response.HttpResponseBuilder;
import org.mule.service.http.impl.NtlmMockResponseGenerator;

import java.io.ByteArrayInputStream;

import io.qameta.allure.Story;
import io.qameta.allure.junit4.DisplayName;
import org.junit.Before;

@Story(STREAMING)
@DisplayName("Validates that the POST body is preserved on NTLM authentication")
public class NtlmHttpClientPostStreamingTestCase extends HttpClientPostStreamingTestCase {

  private NtlmMockResponseGenerator responseGenerator;

  public NtlmHttpClientPostStreamingTestCase(String serviceToLoad) {
    super(serviceToLoad);
  }

  @Override
  @Before
  public void setUp() throws Exception {
    super.setUp();
    responseGenerator = new NtlmMockResponseGenerator("NTLM TlRMTVNTUAABAAAAAYIIogAAAAAoAAAAAAAAACgAAAAFASgKAAAADw==",
                                                      "NTLM TlRMTVNTUAACAAAAAAAAACgAAAABggAAU3J2Tm9uY2UAAAAAAAAAAA==",
                                                      "NTLM TlRMTVNTUAADAAAAGAAYAEgAAAAYABgAYAAAABQAFAB4AAAADAAMAIwAAAAUABQAmAAAAAAAAACsAAAAAYIAAgUBKAoAAAAPrYfKbe/jRoW5xDxHeoxC1gBmfWiS5+iX4OAN4xBKG/IFPwfH3agtPEia6YnhsADTVQBSAFMAQQAtAE0ASQBOAE8AUgBaAGEAcABoAG8AZABwAGIAYQBsAGIAaQAtAGwAdABtAA==");;
  }

  @Override
  public HttpResponse doSetUpHttpResponse(HttpRequest request) {
    HttpResponseBuilder responseBuilder = responseGenerator.generateForRequest(request);

    if (responseGenerator.getState().equals(NtlmMockResponseGenerator.State.SUCCESS) ||
        responseGenerator.getState().equals(NtlmMockResponseGenerator.State.FAILURE)) {
      extractPayload(request);
    }

    return responseBuilder.build();
  }


  @Override
  public HttpRequestOptions getOptions() {
    HttpAuthentication authentication =
        HttpNtlmAuthentication.builder().username("Zaphod").password("Beeblebrox").domain("Ursa-Minor").build();
    return HttpRequestOptions.builder().responseTimeout(RESPONSE_TIMEOUT).authentication(authentication).build();
  }

  @Override
  public HttpRequest getRequest() {
    return HttpRequest.builder().method(POST).uri(getUri())
        .entity(new InputStreamHttpEntity(new ByteArrayInputStream(TEST_PAYLOAD.getBytes()))).build();
  }

}
