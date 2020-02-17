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

import java.io.InputStream;

import org.mule.runtime.http.api.client.HttpRequestOptions;
import org.mule.runtime.http.api.client.auth.HttpAuthentication;
import org.mule.runtime.http.api.client.auth.HttpAuthentication.HttpNtlmAuthentication;
import org.mule.runtime.http.api.domain.entity.InputStreamHttpEntity;
import org.mule.runtime.http.api.domain.message.request.HttpRequest;
import org.mule.runtime.http.api.domain.message.response.HttpResponse;
import org.mule.runtime.http.api.domain.message.response.HttpResponseBuilder;

/**
 * Base test class for post streaming in NTLM
 * 
 */
public abstract class AbstractNtlmHttpClientPostStreamingTestCase extends HttpClientPostStreamingTestCase {

  private static final String HEADER_AUTHORIZATION_NAME = "authorization";
  private static final String USERNAME = "Zaphod";
  private static final String PASSWORD = "Beeblebrox";
  private static final String DOMAIN = "Ursa-Minor";
  private static final String NTLM_HEADER = "NTLM";
  private static final String WWW_AUTHENTICATE = "WWW-Authenticate";
  private static final String NTLM_CORRECT_CHALLENGE_RESPONSE =
      "NTLM TlRMTVNTUAADAAAAGAAYAEgAAAAYABgAYAAAABQAFAB4AAAADAAMAIwAAAAaABoAmAAAAAAAAACyAAAAAYIAAgUBKAoAAAAPrYfKbe/jRoW5xDxHeoxC1gBmfWiS5+iX4OAN4xBKG/IFPwfH3agtPEia6YnhsADTVQBSAFMAQQAtAE0ASQBOAE8AUgBaAGEAcABoAG8AZABCAEEALQBGAEcATwBOAFoAQQAtAE8AUwBYAA==";
  private static final String NTLM_CHALLENGE = "NTLM TlRMTVNTUAACAAAAAAAAACgAAAABggAAU3J2Tm9uY2UAAAAAAAAAAA==";
  private static final String NTLM_MSG1 = "NTLM TlRMTVNTUAABAAAAAYIIogAAAAAoAAAAAAAAACgAAAAFASgKAAAADw==";

  public AbstractNtlmHttpClientPostStreamingTestCase(String serviceToLoad) {
    super(serviceToLoad);
  }

  @Override
  public HttpResponse doSetUpHttpResponse(HttpRequest request) {
    HttpResponseBuilder response = HttpResponse.builder();
    String authorization = request.getHeaderValue(HEADER_AUTHORIZATION_NAME);
    if (authorization == null) {
      response.statusCode(UNAUTHORIZED.getStatusCode());
      response.addHeader(WWW_AUTHENTICATE, NTLM_HEADER);

    } else if (authorization.equals(NTLM_MSG1)) {
      response.statusCode(UNAUTHORIZED.getStatusCode());
      response.addHeader(WWW_AUTHENTICATE, NTLM_CHALLENGE);

    } else if (authorization
        .equals(NTLM_CORRECT_CHALLENGE_RESPONSE)) {
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
        HttpNtlmAuthentication.builder().username(USERNAME).password(PASSWORD).domain(DOMAIN).build();
    return HttpRequestOptions.builder().responseTimeout(RESPONSE_TIMEOUT).authentication(authentication).build();
  }

  @Override
  public HttpRequest getRequest() {
    return HttpRequest.builder().method(POST).uri(getUri())
        .entity(new InputStreamHttpEntity(getInputStream())).build();
  }

  protected abstract InputStream getInputStream();

}
