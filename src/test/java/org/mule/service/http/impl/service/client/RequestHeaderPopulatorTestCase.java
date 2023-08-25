/*
 * Copyright 2023 Salesforce, Inc. All rights reserved.
 */
package org.mule.service.http.impl.service.client;

import static org.mule.runtime.http.api.HttpHeaders.Names.COOKIE;

import static java.lang.Boolean.getBoolean;
import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toList;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assume.assumeThat;
import static org.mockito.Mockito.when;
import static org.mockito.junit.MockitoJUnit.rule;

import org.mule.runtime.http.api.domain.entity.HttpEntity;
import org.mule.runtime.http.api.domain.message.request.HttpRequest;
import org.mule.tck.junit4.AbstractMuleTestCase;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import com.ning.http.client.RequestBuilder;
import com.ning.http.client.cookie.Cookie;
import io.qameta.allure.Issue;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoRule;

public class RequestHeaderPopulatorTestCase extends AbstractMuleTestCase {

  @Rule
  public MockitoRule mockitorule = rule();

  @Mock
  private HttpRequest muleRequest;

  private RequestBuilder ahcRequestBuilder;

  @Mock
  private HttpEntity httpEntity;

  private List<String> headerNames;

  private RequestHeaderPopulator populator;

  @Before
  public void setUp() {
    assumeThat(getBoolean("mule.http.cookie.special.handling.disable"), is(false));

    ahcRequestBuilder = new RequestBuilder();
    headerNames = new ArrayList<>();

    when(muleRequest.getEntity()).thenReturn(httpEntity);
    when(muleRequest.getHeaderNames()).thenReturn(headerNames);

    populator = new RequestHeaderPopulator(true);
  }

  @Test
  public void singleCookiePair() {
    // Given a cookie with only one cookie-pair
    headerNames.add(COOKIE.toLowerCase());
    when(muleRequest.getHeaderValues(COOKIE.toLowerCase())).thenReturn(singletonList("Name=Value"));

    // When the populator handles the headers
    populator.populateHeaders(muleRequest, ahcRequestBuilder);

    // Then the resulting request builder has the corresponding cookie
    Collection<String> cookiesInRequestAsString = getCookiesAsStrings(ahcRequestBuilder);
    assertThat(cookiesInRequestAsString, contains("Name=Value"));
  }

  @Test
  @Issue("W-12666590")
  public void cookieHeaderWithNullValue() {
    // Given a null cookie in the collection
    headerNames.add(COOKIE.toLowerCase());
    when(muleRequest.getHeaderValues(COOKIE.toLowerCase())).thenReturn(singletonList(null));

    // When the populator handles the headers
    populator.populateHeaders(muleRequest, ahcRequestBuilder);

    // Then we don't have a NPE.
  }

  @Test
  @Issue("W-12666590")
  public void cookieHeadersCollectionWithNullValue() {
    // Given a null collection
    headerNames.add(COOKIE.toLowerCase());
    when(muleRequest.getHeaderValues(COOKIE.toLowerCase())).thenReturn(null);

    // When the populator handles the headers
    populator.populateHeaders(muleRequest, ahcRequestBuilder);

    // Then we don't have a NPE.
  }

  @Test
  @Issue("W-12528819")
  public void cookieWithSecureAndHttpOnly() {
    // NOTE: This tests an invalid Cookie syntax given the RFC-6265: https://www.rfc-editor.org/rfc/rfc6265#section-4.2.1
    // The secure and HttpOnly flags are only valid in the Set-Cookie header, and not in the Cookie header.
    // Said that, Mule users usually implement their own cookie handling mechanism by passing the received Set-Cookie
    // header as the Cookie header of the following request. Something like this:
    //
    // <some-connector:make-request />
    // <http:request ...>
    // <http:headers ><![CDATA[#[output application/java
    // ---
    // {
    // "Cookie" : attributes.headers.'Set-Cookie'
    // }]]]></http:headers>
    // </http:request>
    //
    // Therefore, if the Set-Cookie has any of the mentioned flags, the added Cookie header of the next request would
    // have an incorrect syntax, which we're testing here.

    // Given a cookie with a cookie-pair, secure flag, and HttpOnly flag (notice the incorrect syntax).
    headerNames.add(COOKIE.toLowerCase());
    when(muleRequest.getHeaderValues(COOKIE.toLowerCase())).thenReturn(singletonList("Name=Value; secure; HttpOnly"));

    // When the populator handles the headers
    populator.populateHeaders(muleRequest, ahcRequestBuilder);

    // Then the resulting request builder has the corresponding cookie
    Collection<String> cookiesInRequestAsString = getCookiesAsStrings(ahcRequestBuilder);
    assertThat(cookiesInRequestAsString, contains("Name=Value"));
  }

  private static Collection<String> getCookiesAsStrings(RequestBuilder requestBuilder) {
    return requestBuilder.build().getCookies().stream().map(Cookie::toString).collect(toList());
  }
}
