/*
 * Copyright 2023 Salesforce, Inc. All rights reserved.
 */
package org.mule.service.http.impl.functional.client;

import static java.util.Arrays.asList;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.junit.MockitoJUnit.rule;
import static org.mule.runtime.api.util.MuleSystemProperties.ENABLE_MULE_REDIRECT_PROPERTY;
import static org.mule.runtime.http.api.HttpConstants.HttpStatus.MOVED_PERMANENTLY;
import static org.mule.runtime.http.api.HttpConstants.HttpStatus.OK;
import static org.mule.runtime.http.api.HttpHeaders.Names.COOKIE;
import static org.mule.runtime.http.api.HttpHeaders.Names.LOCATION;
import static org.mule.runtime.http.api.HttpHeaders.Names.SET_COOKIE;
import static org.mule.runtime.http.api.domain.message.response.HttpResponse.builder;
import static org.mule.service.http.impl.AllureConstants.HttpFeature.HTTP_SERVICE;

import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runners.Parameterized;
import org.mockito.junit.MockitoRule;
import org.mule.runtime.core.api.util.IOUtils;
import org.mule.runtime.http.api.client.HttpClient;
import org.mule.runtime.http.api.client.HttpClientConfiguration;
import org.mule.runtime.http.api.domain.entity.InputStreamHttpEntity;
import org.mule.runtime.http.api.domain.message.request.HttpRequest;
import org.mule.runtime.http.api.domain.message.response.HttpResponse;
import org.mule.service.http.impl.service.HttpServiceImplementation;
import org.mule.tck.junit4.rule.SystemProperty;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeoutException;

@Feature(HTTP_SERVICE)
@Issue("MULE-19908")
public class GrizzlyHttpClientRedirectTestCase extends AbstractHttpRedirectClientTestCase {

  private HttpClient client;

  @Rule
  public MockitoRule mockitorule = rule();

  @Rule
  public SystemProperty enableRedirect = new SystemProperty(ENABLE_MULE_REDIRECT_PROPERTY, "true");

  private final HttpClientConfiguration.Builder clientBuilder = new HttpClientConfiguration.Builder().setName("redirect-test");

  public boolean streamingMode;

  public GrizzlyHttpClientRedirectTestCase(String serviceToLoad, boolean streamingMode) {
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
  public void sendRequestWithSetCookieHeader() throws IOException, TimeoutException {
    HttpResponse response = client.send(getRequest());
    testRedirectResponse(response);
  }

  @Test
  public void sendAsyncRequestWithSetCookieHeader() throws TimeoutException, ExecutionException, InterruptedException {
    Future<HttpResponse> response = client.sendAsync(getRequest());
    testRedirectResponse(response.get(TIMEOUT, MILLISECONDS));
  }

  private void testRedirectResponse(HttpResponse response) {
    assertThat(response.getStatusCode(), is(OK.getStatusCode()));
    String payload = IOUtils.toString(response.getEntity().getContent());
    assertThat(payload, is(TEST_PAYLOAD));
  }

  private HttpRequest getRequest() {
    return getRequest(getRedirectUri());
  }

  private HttpRequest getRequest(String uri) {
    return HttpRequest.builder().uri(uri).build();
  }

  @Override
  protected HttpResponse setUpHttpResponse(HttpRequest request) {
    assertThat(request.getHeaders().get(COOKIE), is("test1=test1; test2=test2"));
    return builder()
        .statusCode(OK.getStatusCode())
        .reasonPhrase(OK.getReasonPhrase())
        .entity(new InputStreamHttpEntity(new ByteArrayInputStream(TEST_PAYLOAD.getBytes())))
        .build();
  }

  @Override
  protected HttpResponse setUpHttpRedirectResponse(HttpRequest request) {
    return builder()
        .statusCode(MOVED_PERMANENTLY.getStatusCode())
        .reasonPhrase(MOVED_PERMANENTLY.getReasonPhrase())
        .addHeader(LOCATION, getUri())
        .addHeader(SET_COOKIE, "test1=test1")
        .addHeader(SET_COOKIE, "test2=test2thisShouldBeReplaced")
        .addHeader(SET_COOKIE, "test2=test2")
        .addHeader(SET_COOKIE, "test3=test3thisShouldBeRemoved")
        .addHeader(SET_COOKIE, "test3=test3; Expires=Sat, 02 Oct 1993 14:20:00 GMT")
        .build();
  }
}
