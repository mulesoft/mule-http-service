/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.service.http.impl.service.util;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mule.runtime.http.api.HttpHeaders.Names.LOCATION;
import static org.mule.runtime.http.api.domain.HttpProtocol.HTTP_1_1;
import static org.mule.service.http.impl.AllureConstants.HttpFeature.HTTP_SERVICE;
import static org.mule.service.http.impl.service.util.RedirectUtils.createRedirectRequest;

import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.mule.runtime.api.util.MultiMap;
import org.mule.runtime.http.api.domain.entity.HttpEntity;
import org.mule.runtime.http.api.domain.message.request.HttpRequest;
import org.mule.runtime.http.api.domain.message.response.HttpResponse;

import java.net.URI;
import java.net.URISyntaxException;

@RunWith(MockitoJUnitRunner.class)
@Feature(HTTP_SERVICE)
@Issue("MULE-19908")
public class RedirectUtilsTestCase {

  @Mock
  private HttpResponse response;

  @Mock
  private HttpRequest originalRequest;

  private final MultiMap<String, String> responseHeaders = new MultiMap<>();

  @Before
  public void setup() throws URISyntaxException {
    URI originalRequestURI = new URI("http://somehost/originalPath?param=original");
    MultiMap<String, String> originalRequestHeaders = new MultiMap<>();
    HttpEntity originalRequestEntity = mock(HttpEntity.class);
    when(originalRequest.getUri()).thenReturn(originalRequestURI);
    when(originalRequest.getMethod()).thenReturn("GET");
    when(originalRequest.getProtocol()).thenReturn(HTTP_1_1);
    when(originalRequest.getHeaders()).thenReturn(originalRequestHeaders);
    when(originalRequest.getEntity()).thenReturn(originalRequestEntity);

    when(response.getHeaders()).thenReturn(responseHeaders);
    when(response.getStatusCode()).thenReturn(301);
    responseHeaders.put(LOCATION, "http://redirecthost/redirectPath");
  }

  @Test
  public void redirectedRequestWithHostUsesQueryParamsFromResponseLocation() {
    responseHeaders.removeAll(LOCATION);
    responseHeaders.put(LOCATION, "http://redirecthost/redirectPath?param=redirect");

    HttpRequest redirectedRequest = createRedirectRequest(response, originalRequest);
    assertThat(redirectedRequest.getUri().getRawQuery(), is("param=redirect"));
  }

  @Test
  public void redirectedRequestWithoutHostUsesQueryParamsFromResponseLocation() {
    responseHeaders.removeAll(LOCATION);
    responseHeaders.put(LOCATION, "/redirectPath?param=redirect");

    HttpRequest redirectedRequest = createRedirectRequest(response, originalRequest);
    assertThat(redirectedRequest.getUri().getRawQuery(), is("param=redirect"));
  }
}
