/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.service.http.impl.functional.client;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.mule.runtime.http.api.HttpConstants.HttpStatus.OK;
import static org.mule.runtime.http.api.HttpConstants.Method.POST;
import static org.mule.runtime.http.api.HttpHeaders.Names.CONNECTION;
import static org.mule.runtime.http.api.HttpHeaders.Values.CLOSE;
import static org.mule.service.http.impl.AllureConstants.HttpFeature.HttpStory.CLIENT_AUTHENTICATION;
import org.mule.runtime.http.api.client.HttpClient;
import org.mule.runtime.http.api.client.HttpClientConfiguration;
import org.mule.runtime.http.api.client.HttpRequestOptions;
import org.mule.runtime.http.api.client.auth.HttpAuthentication;
import org.mule.runtime.http.api.domain.message.request.HttpRequest;
import org.mule.runtime.http.api.domain.message.response.HttpResponse;
import org.mule.runtime.http.api.domain.message.response.HttpResponseBuilder;
import org.mule.service.http.impl.NtlmMockResponseGenerator;

import io.qameta.allure.Story;
import io.qameta.allure.junit4.DisplayName;
import org.junit.Before;
import org.junit.Test;

@Story(CLIENT_AUTHENTICATION)
@DisplayName("NTLM Authentication should not preserve authenticated sessions in case credentials are variable")
public class NtlmHttpAuthenticatedClientForcesConnectionClose extends AbstractHttpClientTestCase {

  private NtlmMockResponseGenerator ntlmResponseGenerator;
  private boolean connectionCloseSeen = false;

  public NtlmHttpAuthenticatedClientForcesConnectionClose(String serviceToLoad) {
    super(serviceToLoad);
  }

  @Override
  @Before
  public void setUp() throws Exception {
    super.setUp();
    ntlmResponseGenerator = NtlmMockResponseGenerator.forDefaultCredentials();
    connectionCloseSeen = false;
  }

  @Override
  public HttpResponse setUpHttpResponse(HttpRequest request) {
    HttpResponseBuilder responseBuilder = ntlmResponseGenerator.generateForRequest(request);
    if (ntlmResponseGenerator.getState().equals(NtlmMockResponseGenerator.State.SUCCESS)) {
      connectionCloseSeen =
          request.getHeaderNames().contains(CONNECTION) && request.getHeaderValue(CONNECTION).equalsIgnoreCase(CLOSE);
    }
    return responseBuilder.build();
  }

  @Test
  public void lastRequestOfNtlmAuthenticationDanceClosesConnection() throws Exception {
    HttpClient client =
        service.getClientFactory().create(new HttpClientConfiguration.Builder().setName("ntlm authentication testing").build());
    client.start();
    HttpAuthentication authentication =
        HttpAuthentication.HttpNtlmAuthentication.builder()
            .username(ntlmResponseGenerator.getUsername())
            .password(ntlmResponseGenerator.getPassword())
            .domain(ntlmResponseGenerator.getDomain())
            .forceConnectionClose(true)
            .build();
    HttpRequestOptions options = HttpRequestOptions.builder().authentication(authentication).build();
    HttpRequest request = HttpRequest.builder().method(POST).uri(getUri()).build();
    HttpResponse response = client.send(request, options);
    assertThat(response.getStatusCode(), is(OK.getStatusCode()));
    assertThat(connectionCloseSeen, is(true));
  }
}
