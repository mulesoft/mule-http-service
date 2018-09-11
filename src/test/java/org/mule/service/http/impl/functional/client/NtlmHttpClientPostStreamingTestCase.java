/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.service.http.impl.functional.client;

import static org.mule.runtime.http.api.HttpConstants.HttpStatus.OK;
import static org.mule.runtime.http.api.HttpConstants.HttpStatus.UNAUTHORIZED;
import static org.mule.runtime.http.api.HttpConstants.Method.POST;
import static org.mule.service.http.impl.AllureConstants.HttpFeature.HttpStory.STREAMING;

import java.io.ByteArrayInputStream;

import org.mule.runtime.http.api.client.HttpRequestOptions;
import org.mule.runtime.http.api.client.auth.HttpAuthentication;
import org.mule.runtime.http.api.client.auth.HttpAuthentication.HttpNtlmAuthentication;
import org.mule.runtime.http.api.domain.entity.InputStreamHttpEntity;
import org.mule.runtime.http.api.domain.message.request.HttpRequest;
import org.mule.runtime.http.api.domain.message.response.HttpResponse;
import org.mule.runtime.http.api.domain.message.response.HttpResponseBuilder;

import io.qameta.allure.Story;
import io.qameta.allure.junit4.DisplayName;

@Story(STREAMING)
@DisplayName("Validates that the POST body is preserved on NTLM authentication")
public class NtlmHttpClientPostStreamingTestCase extends HttpClientPostStreamingTestCase {

  public NtlmHttpClientPostStreamingTestCase(String serviceToLoad) {
    super(serviceToLoad);
  }

  @Override
  public HttpResponse doSetUpHttpResponse(HttpRequest request) {
    HttpResponseBuilder response = HttpResponse.builder();
    String authorization = request.getHeaderValue("authorization");
    if (authorization == null) {
      response.statusCode(UNAUTHORIZED.getStatusCode());
      response.addHeader("WWW-Authenticate", "NTLM");

    } else if (authorization.equals("NTLM TlRMTVNTUAABAAAAAYIIogAAAAAoAAAAAAAAACgAAAAFASgKAAAADw==")) {
      response.statusCode(UNAUTHORIZED.getStatusCode());
      response.addHeader("WWW-Authenticate", "NTLM TlRMTVNTUAACAAAAAAAAACgAAAABggAAU3J2Tm9uY2UAAAAAAAAAAA==");

    } else if (authorization
        .equals("NTLM TlRMTVNTUAADAAAAGAAYAEgAAAAYABgAYAAAABQAFAB4AAAADAAMAIwAAAAaABoAmAAAAAAAAACyAAAAAYIAAgUBKAoAAAAPrYfKbe/jRoW5xDxHeoxC1gBmfWiS5+iX4OAN4xBKG/IFPwfH3agtPEia6YnhsADTVQBSAFMAQQAtAE0ASQBOAE8AUgBaAGEAcABoAG8AZABCAEEALQBGAEcATwBOAFoAQQAtAE8AUwBYAA==")) {
      extractPayload(request);
      response.statusCode(OK.getStatusCode());
    } else {
      extractPayload(request);
      response.statusCode(UNAUTHORIZED.getStatusCode());
    }

    return response.build();
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
