/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.service.http.impl.service.server.grizzly;

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
@Story(WWW_AUTHENTICATE)
public class HttpRequestMultipleAuthenticationMethodsSupportedTestCase extends AbstractHttpClientTestCase {

  private static final int TIMEOUT_MILLIS = 1000;
  private static final int POLL_DELAY_MILLIS = 200;
  private static final String DIGEST = "Digest";
  private static final String BASIC = "Basic";
  private static final String NONCE =
      "+Upgraded+v1a574e295ff1f41c52582b82815bb734c5c50331c97c4d301bc97f789c5e9e73ca9564b24cbd898ce5f1c13598999faa2ab013ee5b1597087";
  private static boolean choseDigest = false;
  private static boolean choseNtlm = false;
  private static boolean choseBasic = false;

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
    choseBasic = false;
    choseDigest = false;
    choseNtlm = false;

    if (StringUtils.isEmpty(authHeader)) {
      responseBuilder.statusCode(UNAUTHORIZED.getStatusCode())
          .addHeader(WWW_AUTHENTICATE, BASIC)
          .addHeader(WWW_AUTHENTICATE, NTLM.name())
          .addHeader(WWW_AUTHENTICATE,
                     DIGEST + " qop=\"auth\",algorithm=MD5-sess," +
                         "nonce=\"" + NONCE + "\",charset=utf-8,realm=\"INT\"");
    } else if (authHeader.startsWith(DIGEST) && authHeader.contains(NONCE)) {
      responseBuilder.statusCode(OK.getStatusCode())
          .reasonPhrase(OK.getReasonPhrase());
      choseDigest = true;
    } else if (authHeader.startsWith(BASIC)) {
      responseBuilder.statusCode(OK.getStatusCode())
          .reasonPhrase(OK.getReasonPhrase());
      choseBasic = true;
    } else if (authHeader.startsWith(NTLM.name())) {
      responseBuilder.statusCode(OK.getStatusCode())
          .reasonPhrase(OK.getReasonPhrase());
      choseNtlm = true;
    } else {
      responseBuilder.statusCode(UNAUTHORIZED.getStatusCode());
    }
    return responseBuilder.build();
  }

  public HttpRequestOptions getOptionsDigest() {
    HttpAuthentication authentication =
        HttpAuthentication.digest("user", "password").preemptive(false).build();
    return HttpRequestOptions.builder().authentication(authentication).build();
  }

  public HttpRequestOptions getOptionsNtlm() {
    HttpAuthentication authentication =
        HttpAuthentication.ntlm("user", "password").preemptive(false).build();
    return HttpRequestOptions.builder().authentication(authentication).build();
  }

  public HttpRequestOptions getOptionsBasic() {
    HttpAuthentication authentication =
        HttpAuthentication.basic("user", "password").preemptive(false).build();
    return HttpRequestOptions.builder().authentication(authentication).build();
  }

  public HttpRequest getRequest() {
    return HttpRequest.builder().method(GET).uri(getUri()).build();
  }

  @Before
  public void setUpClient() {
    client = service.getClientFactory().create(clientBuilder.build());
    client.start();
  }

  @After
  public void stopClient() {
    client.stop();
  }

  @Test
  public void onMultipleAuthenticationMethodsSupportedLocallySetMethodIsChosenDigest() {
    HttpRequestOptions options = getOptionsDigest();
    HttpRequest request = getRequest();
    client.sendAsync(request, options).whenComplete(
                                                    (response, exception) -> responseReference.set(response));

    pollingProber.check(new ResponseReceivedProbe(responseReference));
    pollingProber.check(new Probe() {

      @Override
      public boolean isSatisfied() {
        return choseDigest;
      }

      @Override
      public String describeFailure() {
        return "Authorization method chosen should be Digest!";
      }

    });
  }

  @Test
  public void onMultipleAuthenticationMethodsSupportedLocallySetMethodIsChosenNtlm() {
    HttpRequestOptions options = getOptionsNtlm();
    HttpRequest request = getRequest();
    client.sendAsync(request, options).whenComplete(
                                                    (response, exception) -> responseReference.set(response));

    pollingProber.check(new ResponseReceivedProbe(responseReference));
    pollingProber.check(new Probe() {

      @Override
      public boolean isSatisfied() {
        return choseNtlm;
      }

      @Override
      public String describeFailure() {
        return "Authorization method chosen should be Ntlm!";
      }

    });
  }

  @Test
  public void onMultipleAuthenticationMethodsSupportedLocallySetMethodIsChosenBasic() {
    HttpRequestOptions options = getOptionsBasic();
    HttpRequest request = getRequest();
    client.sendAsync(request, options).whenComplete(
                                                    (response, exception) -> responseReference.set(response));
    pollingProber.check(new ResponseReceivedProbe(responseReference));
    pollingProber.check(new Probe() {

      @Override
      public boolean isSatisfied() {
        return choseBasic;
      }

      @Override
      public String describeFailure() {
        return "Authorization method chosen should be Basic!";
      }

    });
  }
}
