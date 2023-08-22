/*
 * Copyright 2023 Salesforce, Inc. All rights reserved.
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.service.http.impl.service.server.grizzly;

import static org.mule.runtime.core.api.util.StringUtils.isEmpty;
import static org.mule.runtime.http.api.HttpConstants.HttpStatus.OK;
import static org.mule.runtime.http.api.HttpConstants.HttpStatus.UNAUTHORIZED;
import static org.mule.runtime.http.api.HttpConstants.Method.GET;
import static org.mule.service.http.impl.AllureConstants.HttpFeature.HTTP_SERVICE;
import static org.mule.service.http.impl.AllureConstants.HttpFeature.HttpStory.CLIENT_AUTHENTICATION;

import static org.apache.http.HttpHeaders.AUTHORIZATION;
import static org.apache.http.HttpHeaders.WWW_AUTHENTICATE;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.text.IsEqualIgnoringCase.equalToIgnoringCase;

import org.mule.runtime.api.util.Reference;
import org.mule.runtime.http.api.client.HttpClient;
import org.mule.runtime.http.api.client.HttpClientConfiguration;
import org.mule.runtime.http.api.client.HttpRequestOptions;
import org.mule.runtime.http.api.client.auth.HttpAuthentication;
import org.mule.runtime.http.api.client.auth.HttpAuthenticationType;
import org.mule.runtime.http.api.domain.message.request.HttpRequest;
import org.mule.runtime.http.api.domain.message.response.HttpResponse;
import org.mule.runtime.http.api.domain.message.response.HttpResponseBuilder;
import org.mule.service.http.impl.functional.ResponseReceivedProbe;
import org.mule.service.http.impl.functional.client.AbstractHttpClientTestCase;
import org.mule.tck.probe.PollingProber;
import org.mule.tck.probe.Probe;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import io.qameta.allure.Feature;
import io.qameta.allure.Story;

@Feature(HTTP_SERVICE)
@Story(CLIENT_AUTHENTICATION)
public class HttpRequestMultipleAuthenticationMethodsSupportedTestCase extends AbstractHttpClientTestCase {

  private static final int TIMEOUT_MILLIS = 1000;
  private static final int POLL_DELAY_MILLIS = 200;
  private static final String NONCE =
      "+Upgraded+v1a574e295ff1f41c52582b82815bb734c5c50331c97c4d301bc97f789c5e9e73ca9564b24cbd898ce5f1c13598999faa2ab013ee5b1597087";
  private static final String TEST_USER = "user";
  private static final String TEST_PASS = "password";

  private static final String BASIC_AUTH_METHOD = "BASIC";
  private static final String DIGEST_AUTH_METHOD = "DIGEST";
  private static final String NTLM_AUTH_METHOD = "NTLM";


  private HttpClient client;
  private String chosenAuthMethod = "";
  private final HttpClientConfiguration.Builder clientBuilder = new HttpClientConfiguration.Builder().setName("auth-test");
  private final PollingProber pollingProber = new PollingProber(TIMEOUT_MILLIS, POLL_DELAY_MILLIS);
  private final Reference<HttpResponse> responseReference = new Reference<>();

  public HttpRequestMultipleAuthenticationMethodsSupportedTestCase(String serviceToLoad) {
    super(serviceToLoad);
  }

  @Before
  public void setUpClient() {
    client = service.getClientFactory().create(clientBuilder.build());
    client.start();
  }

  @After
  public void stopClient() {
    if (client != null) {
      client.stop();
    }
  }

  @Test
  public void onMultipleAuthenticationMethodsSupportedLocallySetMethodIsChosenDigest() {
    sendRequestAssertCorrectAuthMethodChosen(DIGEST_AUTH_METHOD);
  }

  @Test
  public void onMultipleAuthenticationMethodsSupportedLocallySetMethodIsChosenNtlm() {
    sendRequestAssertCorrectAuthMethodChosen(NTLM_AUTH_METHOD);
  }

  @Test
  public void onMultipleAuthenticationMethodsSupportedLocallySetMethodIsChosenBasic() {
    sendRequestAssertCorrectAuthMethodChosen(BASIC_AUTH_METHOD);
  }

  @Override
  protected HttpResponse setUpHttpResponse(HttpRequest request) {
    HttpResponseBuilder responseBuilder = HttpResponse.builder();
    String authHeader = request.getHeaderValue(AUTHORIZATION);
    chosenAuthMethod = getRequestAuthType(authHeader);

    if (isEmpty(authHeader)) {
      responseBuilder.statusCode(UNAUTHORIZED.getStatusCode())
          .addHeader(WWW_AUTHENTICATE, BASIC_AUTH_METHOD)
          .addHeader(WWW_AUTHENTICATE, NTLM_AUTH_METHOD)
          .addHeader(WWW_AUTHENTICATE,
                     DIGEST_AUTH_METHOD + " qop=\"auth\",algorithm=MD5-sess," +
                         "nonce=\"" + NONCE + "\",charset=utf-8,realm=\"INT\"");
    } else if (chosenAuthMethod.length() > 0) {
      responseBuilder.statusCode(OK.getStatusCode())
          .reasonPhrase(OK.getReasonPhrase());
    } else {
      responseBuilder.statusCode(UNAUTHORIZED.getStatusCode());
    }
    return responseBuilder.build();
  }

  protected void sendRequestAssertCorrectAuthMethodChosen(String authMethod) {
    HttpRequestOptions options = getOptions(authMethod);
    HttpRequest request = getRequest();
    client.sendAsync(request, options).whenComplete(
                                                    (response, exception) -> responseReference.set(response));
    pollingProber.check(new ResponseReceivedProbe(responseReference));
    pollingProber.check(new ResponseSuccessProbe(authMethod));
    assertThat(chosenAuthMethod, equalToIgnoringCase(authMethod));
  }

  private HttpRequestOptions getOptions(String authMethod) {
    HttpAuthentication auth = HttpAuthentication.builder()
        .type(HttpAuthenticationType.valueOf(authMethod))
        .username(TEST_USER)
        .password(TEST_PASS)
        .preemptive(false)
        .build();
    return HttpRequestOptions.builder().authentication(auth).build();
  }

  private HttpRequest getRequest() {
    return HttpRequest.builder().method(GET).uri(getUri()).build();
  }

  private String getRequestAuthType(String authHeader) {
    return isEmpty(authHeader) ? "" : authHeader.substring(0, authHeader.indexOf(" "));
  }

  private class ResponseSuccessProbe implements Probe {

    private final String authType;

    public ResponseSuccessProbe(String httpAuthType) {
      authType = httpAuthType;
    }

    @Override
    public boolean isSatisfied() {
      return responseReference.get().getStatusCode() == OK.getStatusCode();
    }

    @Override
    public String describeFailure() {
      return "Authorization method chosen should be " + authType;
    }
  }
}
