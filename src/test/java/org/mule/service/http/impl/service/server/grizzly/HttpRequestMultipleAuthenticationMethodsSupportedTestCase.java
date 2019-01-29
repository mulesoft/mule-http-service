/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.service.http.impl.service.server.grizzly;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.IsEqual.equalTo;
import static org.mule.service.http.impl.AllureConstants.HttpFeature.HttpStory.CLIENT_AUTHENTICATION;
import static sun.net.www.protocol.http.AuthScheme.NTLM;
import static org.mule.runtime.http.api.HttpConstants.HttpStatus.UNAUTHORIZED;
import static org.mule.runtime.http.api.HttpConstants.HttpStatus.OK;
import static org.mule.runtime.http.api.HttpConstants.Method.GET;
import static org.apache.http.HttpHeaders.WWW_AUTHENTICATE;
import static org.apache.http.HttpHeaders.AUTHORIZATION;
import static org.mule.service.http.impl.AllureConstants.HttpFeature.HTTP_SERVICE;

import org.junit.After;
import org.junit.Before;
import org.mule.runtime.api.util.Reference;
import org.mule.runtime.core.api.util.StringUtils;
import org.mule.runtime.http.api.client.HttpClient;
import org.mule.runtime.http.api.client.HttpClientConfiguration;
import org.mule.runtime.http.api.client.HttpRequestOptions;
import org.mule.runtime.http.api.client.auth.HttpAuthentication;
import org.mule.runtime.http.api.client.auth.HttpAuthenticationBuilder;
import org.mule.runtime.http.api.domain.message.request.HttpRequest;
import org.mule.runtime.http.api.domain.message.response.HttpResponse;
import org.mule.runtime.http.api.domain.message.response.HttpResponseBuilder;
import org.mule.service.http.impl.functional.ResponseReceivedProbe;
import org.mule.service.http.impl.functional.client.AbstractHttpClientTestCase;

import io.qameta.allure.Feature;
import io.qameta.allure.Story;
import org.junit.Test;
import org.mule.tck.probe.PollingProber;
import org.mule.tck.probe.Probe;

@Feature(HTTP_SERVICE)
@Story(CLIENT_AUTHENTICATION)
public class HttpRequestMultipleAuthenticationMethodsSupportedTestCase extends AbstractHttpClientTestCase {

  private static final int TIMEOUT_MILLIS = 1000;
  private static final int POLL_DELAY_MILLIS = 200;
  private static final String DIGEST = "Digest";
  private static final String BASIC = "Basic";
  private static final String NONCE =
      "+Upgraded+v1a574e295ff1f41c52582b82815bb734c5c50331c97c4d301bc97f789c5e9e73ca9564b24cbd898ce5f1c13598999faa2ab013ee5b1597087";
  private static String chosenAuthMethod = "";
  private static String TEST_USER = "user";
  private static String TEST_PASS = "password";
  private static HttpClient client;

  private HttpClientConfiguration.Builder clientBuilder = new HttpClientConfiguration.Builder().setName("auth-test");
  private PollingProber pollingProber = new PollingProber(TIMEOUT_MILLIS, POLL_DELAY_MILLIS);
  private final Reference<HttpResponse> responseReference = new Reference<>();

  public HttpRequestMultipleAuthenticationMethodsSupportedTestCase(String serviceToLoad) {
    super(serviceToLoad);
  }

  @Override
  protected HttpResponse setUpHttpResponse(HttpRequest request) {
    HttpResponseBuilder responseBuilder = HttpResponse.builder();
    String authHeader = request.getHeaderValue(AUTHORIZATION);
    String authType = getRequestAuthType(authHeader);
    chosenAuthMethod = "";

    if (StringUtils.isEmpty(authHeader)) {
      responseBuilder.statusCode(UNAUTHORIZED.getStatusCode())
          .addHeader(WWW_AUTHENTICATE, BASIC)
          .addHeader(WWW_AUTHENTICATE, NTLM.name())
          .addHeader(WWW_AUTHENTICATE,
                     DIGEST + " qop=\"auth\",algorithm=MD5-sess," +
                         "nonce=\"" + NONCE + "\",charset=utf-8,realm=\"INT\"");
    } else if (authType.length() > 0) {
      chosenAuthMethod = authType;
      responseBuilder.statusCode(OK.getStatusCode())
          .reasonPhrase(OK.getReasonPhrase());
    } else {
      responseBuilder.statusCode(UNAUTHORIZED.getStatusCode());
    }
    return responseBuilder.build();
  }

  public HttpRequestOptions getOptions(HttpAuthenticationBuilder authenticationBuilder) {
    HttpAuthentication authentication = authenticationBuilder.build();
    return HttpRequestOptions.builder().authentication(authentication).build();
  }

  public HttpRequest getRequest() {
    return HttpRequest.builder().method(GET).uri(getUri()).build();
  }

  public String getRequestAuthType(String authHeader) {
    return StringUtils.isEmpty(authHeader) ? "" : authHeader.substring(0, authHeader.indexOf(" "));
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
    sendRequestAssertCorrectAuthMethodChosen(HttpAuthentication.digest(TEST_USER, TEST_PASS), DIGEST);
  }

  @Test
  public void onMultipleAuthenticationMethodsSupportedLocallySetMethodIsChosenNtlm() {
    sendRequestAssertCorrectAuthMethodChosen(HttpAuthentication.ntlm(TEST_USER, TEST_PASS), NTLM.name());
  }

  @Test
  public void onMultipleAuthenticationMethodsSupportedLocallySetMethodIsChosenBasic() {
    sendRequestAssertCorrectAuthMethodChosen(HttpAuthentication.basic(TEST_USER, TEST_PASS), BASIC);
  }

  protected void sendRequestAssertCorrectAuthMethodChosen(HttpAuthenticationBuilder authBuilder, String authMethod) {
    HttpRequestOptions options = getOptions(authBuilder);
    HttpRequest request = getRequest();
    client.sendAsync(request, options).whenComplete(
                                                    (response, exception) -> responseReference.set(response));
    pollingProber.check(new ResponseReceivedProbe(responseReference));
    pollingProber.check(new ResponseSuccessProbe());
    assertThat(chosenAuthMethod, equalTo(authMethod));
  }

  private class ResponseSuccessProbe implements Probe {

    @Override
    public boolean isSatisfied() {
      return responseReference.get().getStatusCode() == OK.getStatusCode();
    }

    @Override
    public String describeFailure() {
      return "Authorization method chosen should be Basic!";
    }
  }
}
