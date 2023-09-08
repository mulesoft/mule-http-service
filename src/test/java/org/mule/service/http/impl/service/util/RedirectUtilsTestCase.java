/*
 * Copyright 2023 Salesforce, Inc. All rights reserved.
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.service.http.impl.service.util;

import static org.mule.runtime.api.util.MultiMap.emptyMultiMap;
import static org.mule.runtime.http.api.HttpHeaders.Names.LOCATION;
import static org.mule.runtime.http.api.HttpHeaders.Names.SET_COOKIE;
import static org.mule.runtime.http.api.client.auth.HttpAuthenticationType.NTLM;
import static org.mule.runtime.http.api.domain.HttpProtocol.HTTP_1_1;
import static org.mule.service.http.impl.AllureConstants.HttpFeature.HTTP_SERVICE;

import static java.util.Arrays.asList;
import static java.util.Optional.empty;
import static java.util.Optional.of;
import static java.util.stream.Collectors.toList;

import static org.glassfish.grizzly.http.util.Header.Authorization;
import static org.glassfish.grizzly.http.util.Header.ContentLength;
import static org.glassfish.grizzly.http.util.Header.Host;
import static org.glassfish.grizzly.http.util.Header.ProxyAuthorization;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.junit.MockitoJUnit.rule;

import org.mule.runtime.api.util.MultiMap;
import org.mule.runtime.http.api.client.HttpRequestOptions;
import org.mule.runtime.http.api.client.auth.HttpAuthentication;
import org.mule.runtime.http.api.domain.entity.EmptyHttpEntity;
import org.mule.runtime.http.api.domain.entity.HttpEntity;
import org.mule.runtime.http.api.domain.message.request.HttpRequest;
import org.mule.runtime.http.api.domain.message.response.HttpResponse;
import org.mule.tck.junit4.AbstractMuleTestCase;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collection;
import java.util.stream.Collectors;

import com.ning.http.client.RequestBuilder;
import com.ning.http.client.cookie.Cookie;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.mockito.Mock;
import org.mockito.junit.MockitoRule;

@RunWith(Parameterized.class)
@Feature(HTTP_SERVICE)
@Issue("MULE-19908")
public class RedirectUtilsTestCase extends AbstractMuleTestCase {

  @Rule
  public MockitoRule mockitorule = rule();
  @Mock
  private HttpResponse response;

  @Mock
  private HttpRequest originalRequest;

  @Mock
  private HttpRequestOptions options;

  @Mock
  private HttpAuthentication httpAuthentication;

  private final MultiMap<String, String> originalRequestHeaders = new MultiMap<>();

  private final MultiMap<String, String> responseHeaders = new MultiMap<>();

  @Parameterized.Parameter
  public String method;

  @Parameterized.Parameters(name = "{0}")
  public static Collection<Object[]> data() {
    return asList(new Object[][] {
        {"POST"},
        {"GET"},
        {"PUT"}
    });
  }

  @Before
  public void setup() throws URISyntaxException {
    originalRequestHeaders.clear();
    URI originalRequestURI = new URI("http://somehost/originalPath?param=original");
    HttpEntity originalRequestEntity = mock(HttpEntity.class);
    when(originalRequest.getUri()).thenReturn(originalRequestURI);
    when(originalRequest.getProtocol()).thenReturn(HTTP_1_1);
    when(originalRequest.getHeaders()).thenReturn(originalRequestHeaders);
    when(originalRequest.getEntity()).thenReturn(originalRequestEntity);
    when(originalRequest.getMethod()).thenReturn(method);

    when(options.getAuthentication()).thenReturn(empty());

    when(response.getHeaders()).thenReturn(responseHeaders);
    when(response.getHeaderValues(anyString()))
        .thenAnswer(invocationOnMock -> responseHeaders.getAll(invocationOnMock.getArgument(0, String.class)));

    responseHeaders.put(LOCATION, "http://redirecthost/redirectPath");
  }

  @Test
  public void redirectedRequestWithHostUsesQueryParamsFromResponseLocation() {
    when(response.getStatusCode()).thenReturn(301);
    testRedirectRequest("http://redirecthost/redirectPath?param=redirect", method, false, false);
  }

  @Test
  public void redirectedRequestWithoutHostUsesQueryParamsFromResponseLocation() {
    when(response.getStatusCode()).thenReturn(301);
    testRedirectRequest("/redirectPath?param=redirect", method, false, false);
  }

  @Test
  public void redirectedRequestWith302AndPostMethod() {
    when(originalRequest.getMethod()).thenReturn("POST");
    when(response.getStatusCode()).thenReturn(302);
    testRedirectRequest("/redirectPath?param=redirect", "GET", false, false);
  }

  @Test
  @Issue("W-12594415")
  public void redirectedRequestWith302AndPostMethodWithoutSendBodyAlways() {
    when(originalRequest.getMethod()).thenReturn("POST");
    when(response.getStatusCode()).thenReturn(302);
    when(options.shouldSendBodyAlways()).thenReturn(false);
    RedirectUtils redirectUtils = new RedirectUtils(false, false);
    HttpRequest redirectedRequest = redirectUtils.createRedirectRequest(response, originalRequest, options);
    assertThat(redirectedRequest.getMethod(), is("GET"));
    assertThat(redirectedRequest.getEntity(), instanceOf(EmptyHttpEntity.class));
  }

  @Test
  @Issue("W-12594415")
  public void redirectedRequestWith302AndPostMethodWithSendBodyAlways() {
    when(originalRequest.getMethod()).thenReturn("POST");
    when(response.getStatusCode()).thenReturn(302);
    when(options.shouldSendBodyAlways()).thenReturn(true);
    RedirectUtils redirectUtils = new RedirectUtils(false, false);
    HttpRequest redirectedRequest = redirectUtils.createRedirectRequest(response, originalRequest, options);
    assertThat(redirectedRequest.getMethod(), is("GET"));
    assertThat(redirectedRequest.getEntity(), is(originalRequest.getEntity()));
  }

  @Test
  public void redirectedRequestWith302AndPostMethodWithStrict302Handling() {
    when(originalRequest.getMethod()).thenReturn("POST");
    when(response.getStatusCode()).thenReturn(302);
    testRedirectRequest("/redirectPath?param=redirect", "POST", true, false);
  }

  @Test
  public void redirectedRequestWith303AndPostMethod() {
    when(originalRequest.getMethod()).thenReturn("POST");
    when(response.getStatusCode()).thenReturn(303);
    testRedirectRequest("/redirectPath?param=redirect", "GET", false, false);
  }

  @Test
  public void redirectedRequestWithHostHeader() {
    when(response.getStatusCode()).thenReturn(301);
    originalRequestHeaders.put(Host.toString(), "HOST");
    testRedirectRequest("/redirectPath?param=redirect", method, false, false);
  }

  @Test
  public void redirectedRequestWithContentLengthHeader() {
    when(response.getStatusCode()).thenReturn(301);
    originalRequestHeaders.put(ContentLength.toString(), "ContentLength");
    testRedirectRequest("/redirectPath?param=redirect", method, false, false);
  }

  @Test
  public void redirectedRequestWithNTLM() {
    when(response.getStatusCode()).thenReturn(301);
    originalRequestHeaders.put(ContentLength.toString(), "ContentLength");

    when(options.getAuthentication()).thenReturn(of(httpAuthentication));
    when(httpAuthentication.getType()).thenReturn(NTLM);

    originalRequestHeaders.put(Authorization.toString(), "Authorization");
    originalRequestHeaders.put(ProxyAuthorization.toString(), "ProxyAuthorization");

    HttpRequest redirectedRequest = testRedirectRequest("/redirectPath?param=redirect", method, false, false);

    assertThat(redirectedRequest.getHeaders().containsKey(Authorization.toString()), is(false));
    assertThat(redirectedRequest.getHeaders().containsKey(ProxyAuthorization.toString()), is(false));
  }

  @Test
  @Issue("W-10822777")
  public void caseSensitivity() {
    when(response.getStatusCode()).thenReturn(301);
    String testString = "CaseSensitive";
    originalRequestHeaders.put(testString, testString);

    HttpRequest redirectedRequest = testRedirectRequest("/redirectPath?param=redirect", method, false, true);

    assertThat(redirectedRequest.getHeaders().entryList().get(0).getKey(), is(testString));
  }

  @Test
  public void whenMuleRedirectIsDisabledTheShouldFollowRedirectMethodReturnsFalse() {
    RedirectUtils redirectUtils = new RedirectUtils(false, false);
    boolean shouldFollowRedirect = redirectUtils.shouldFollowRedirect(null, null, false);
    assertThat(shouldFollowRedirect, is(false));
  }

  @Test
  public void whenResponseIs200OKItShouldNotFollowRedirect() {
    when(response.getStatusCode()).thenReturn(200);
    RedirectUtils redirectUtils = new RedirectUtils(false, false);
    boolean shouldFollowRedirect = redirectUtils.shouldFollowRedirect(response, options, true);
    assertThat(shouldFollowRedirect, is(false));
  }

  @Test
  public void whenResponseIs301ButLocationHeaderIsAbsentItShouldNotFollowRedirect() {
    when(response.getStatusCode()).thenReturn(301);
    when(response.getHeaders()).thenReturn(emptyMultiMap());

    RedirectUtils redirectUtils = new RedirectUtils(false, false);
    boolean shouldFollowRedirect = redirectUtils.shouldFollowRedirect(response, options, true);
    assertThat(shouldFollowRedirect, is(false));
  }

  @Test
  public void whenOptionHasTheFlagDisabledThenItShouldNotFollowRedirect() {
    when(response.getStatusCode()).thenReturn(301);
    when(options.isFollowsRedirect()).thenReturn(false);

    RedirectUtils redirectUtils = new RedirectUtils(false, false);
    boolean shouldFollowRedirect = redirectUtils.shouldFollowRedirect(response, options, true);
    assertThat(shouldFollowRedirect, is(false));
  }

  @Test
  public void whenStatusCode301LocationPresentAndOptionEnabledItFollowsRedirects() {
    RedirectUtils redirectUtils = new RedirectUtils(false, false);
    when(response.getStatusCode()).thenReturn(301);
    when(options.isFollowsRedirect()).thenReturn(true);

    boolean shouldFollowRedirect = redirectUtils.shouldFollowRedirect(response, options, true);
    assertThat(shouldFollowRedirect, is(true));
  }

  @Test
  @Issue("W-12161504")
  public void setCookieHeaderInResponseIsAddedAsCookieInNextRequest() {
    RedirectUtils redirectUtils = new RedirectUtils(false, false);
    addSetCookieHeader("TheCookieName=TheCookieValue");

    RequestBuilder requestBuilder = new RequestBuilder();
    redirectUtils.handleResponseCookies(requestBuilder, response);
    Collection<String> cookiesInRequestAsString = getCookiesAsStrings(requestBuilder);
    assertThat(cookiesInRequestAsString, contains("TheCookieName=TheCookieValue"));
  }

  @Test
  @Issue("W-12161504")
  public void setTwoCookiesWithSameName() {
    RedirectUtils redirectUtils = new RedirectUtils(false, false);
    addSetCookieHeader("TheCookieName=OldCookieValue");
    addSetCookieHeader("TheCookieName=NewCookieValue");

    RequestBuilder requestBuilder = new RequestBuilder();
    redirectUtils.handleResponseCookies(requestBuilder, response);
    Collection<String> cookiesInRequestAsString = getCookiesAsStrings(requestBuilder);
    assertThat(cookiesInRequestAsString, contains("TheCookieName=NewCookieValue"));
  }

  private static Collection<String> getCookiesAsStrings(RequestBuilder requestBuilder) {
    return requestBuilder.build().getCookies().stream().map(Cookie::toString).collect(toList());
  }

  @Test
  @Issue("W-12161504")
  public void setCookieWithDateInThePastRemovesIt() {
    RedirectUtils redirectUtils = new RedirectUtils(false, false);
    addSetCookieHeader("TheCookieName=TheCookieValue");
    addSetCookieHeader("TheCookieName=NewCookieValue; Expires=Sat, 02 Oct 1993 14:20:00 GMT");

    RequestBuilder requestBuilder = new RequestBuilder();
    redirectUtils.handleResponseCookies(requestBuilder, response);
    Collection<String> cookiesInRequestAsString = getCookiesAsStrings(requestBuilder);
    assertThat(cookiesInRequestAsString.isEmpty(), is(true));
  }

  private void addSetCookieHeader(String setCookieHeader) {
    responseHeaders.put(SET_COOKIE, setCookieHeader);
  }

  private HttpRequest testRedirectRequest(String path, String method, boolean isStrict302Handling, boolean preserveHeaderCase) {
    setLocationHeader(path);
    RedirectUtils redirectUtils = new RedirectUtils(isStrict302Handling, preserveHeaderCase);
    HttpRequest redirectedRequest = redirectUtils.createRedirectRequest(response, originalRequest, options);

    assertThat(redirectedRequest.getUri().getRawQuery(), is("param=redirect"));
    assertThat(redirectedRequest.getMethod(), is(method));
    assertThat(redirectedRequest.getHeaders().containsKey(Host.toString()), is(false));
    assertThat(redirectedRequest.getHeaders().containsKey(ContentLength.toString()), is(false));

    return redirectedRequest;
  }

  private void setLocationHeader(String path) {
    responseHeaders.removeAll(LOCATION);
    responseHeaders.put(LOCATION, path);
  }
}
