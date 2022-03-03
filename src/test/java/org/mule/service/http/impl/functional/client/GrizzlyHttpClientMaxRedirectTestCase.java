/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.service.http.impl.functional.client;

import static java.util.Arrays.asList;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.junit.MockitoJUnit.rule;
import static org.mule.runtime.api.util.MuleSystemProperties.ENABLE_MULE_REDIRECT_PROPERTY;
import static org.mule.runtime.http.api.HttpConstants.HttpStatus.MOVED_PERMANENTLY;
import static org.mule.runtime.http.api.HttpHeaders.Names.LOCATION;
import static org.mule.service.http.impl.AllureConstants.HttpFeature.HTTP_SERVICE;
import static org.mule.service.http.impl.service.client.GrizzlyHttpClient.MAX_REDIRECTS;
import static org.mule.service.http.impl.service.client.GrizzlyHttpClient.refreshSystemProperties;

import com.ning.http.client.MaxRedirectException;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.mockito.junit.MockitoRule;
import org.mule.runtime.http.api.client.HttpClient;
import org.mule.runtime.http.api.client.HttpClientConfiguration;
import org.mule.runtime.http.api.domain.message.request.HttpRequest;
import org.mule.runtime.http.api.domain.message.response.HttpResponse;
import org.mule.service.http.impl.service.HttpServiceImplementation;
import org.mule.tck.junit4.rule.SystemProperty;

@Feature(HTTP_SERVICE)
@RunWith(Parameterized.class)
@Issue("MULE-19908")
public class GrizzlyHttpClientMaxRedirectTestCase extends AbstractHttpClientTestCase {

  @Rule
  public MockitoRule mockitorule = rule();

  @Rule
  public SystemProperty enableRedirect = new SystemProperty(ENABLE_MULE_REDIRECT_PROPERTY, "true");

  private final HttpClientConfiguration.Builder clientBuilder =
      new HttpClientConfiguration.Builder().setName("max-redirect-test");

  private int currentRedirects;

  private HttpClient client;

  public boolean streamingMode;

  public GrizzlyHttpClientMaxRedirectTestCase(String serviceToLoad, boolean streamingMode) {
    super(serviceToLoad);
    this.streamingMode = streamingMode;
  }

  @Parameterized.Parameters(name = "streaming mode: {1}")
  public static Iterable<Object[]> params() {
    return asList(new Object[][] {
        {HttpServiceImplementation.class.getName(), true},
        {HttpServiceImplementation.class.getName(), false}
    });
  }

  @Before
  public void before() {
    refreshSystemProperties();
    currentRedirects = 0;
    client = service.getClientFactory().create(clientBuilder.setStreaming(streamingMode).build());
    client.start();
  }

  @After
  public void stopClient() {
    if (client != null) {
      client.stop();
    }
  }

  @Test
  public void sendRequestAndFailedWithMaxRedirectException() {
    try {
      client.send(getRequest());
    } catch (Exception e) {
      testMaxRedirect(e);
    }
  }

  @Test
  public void sendAsyncRequestAndFailedWithMaxRedirectException() {
    try {
      client.sendAsync(getRequest()).get();
    } catch (Exception e) {
      testMaxRedirect(e);
    }
  }

  private void testMaxRedirect(Exception e) {
    assertThat(e.getCause().getClass(), is(MaxRedirectException.class));
    // +1 because the first request is not a redirect
    assertThat(currentRedirects, is(MAX_REDIRECTS + 1));
  }

  private HttpRequest getRequest() {
    return getRequest(getUri());
  }

  private HttpRequest getRequest(String uri) {
    return HttpRequest.builder().uri(uri).build();
  }

  @Override
  protected HttpResponse setUpHttpResponse(HttpRequest request) {
    currentRedirects++;
    return HttpResponse.builder()
        .statusCode(MOVED_PERMANENTLY.getStatusCode())
        .reasonPhrase(MOVED_PERMANENTLY.getReasonPhrase())
        .addHeader(LOCATION, getUri())
        .build();
  }
}
