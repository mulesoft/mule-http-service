/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.service.http.impl.service.server.grizzly;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;
import static org.mule.runtime.http.api.HttpConstants.Method.GET;
import static org.mule.runtime.http.api.HttpConstants.Method.OPTIONS;
import static org.mule.runtime.http.api.HttpConstants.Method.PATCH;
import static org.mule.runtime.http.api.HttpConstants.Method.POST;
import static org.mule.service.http.impl.AllureConstants.HttpFeature.HTTP_SERVICE;
import static org.mule.service.http.impl.AllureConstants.HttpFeature.HttpStory.LISTENERS;

import org.mule.runtime.http.api.HttpConstants.Method;
import org.mule.runtime.http.api.domain.message.request.HttpRequest;
import org.mule.runtime.http.api.domain.message.request.HttpRequestBuilder;
import org.mule.runtime.http.api.server.MethodRequestMatcher;
import org.mule.tck.junit4.AbstractMuleTestCase;
import org.mule.tck.size.SmallTest;

import org.junit.Test;
import io.qameta.allure.Feature;
import io.qameta.allure.Story;

@SmallTest
@Feature(HTTP_SERVICE)
@Story(LISTENERS)
public class DefaultMethodRequestMatcherTestCase extends AbstractMuleTestCase {

  private HttpRequestBuilder requestBuilder = HttpRequest.builder().uri("uri");

  @Test(expected = IllegalArgumentException.class)
  public void doNotAcceptsEmptyString() {
    new DefaultMethodRequestMatcher(new String[] {});
  }

  @Test(expected = IllegalArgumentException.class)
  public void doNotAcceptsEmptyMethod() {
    new DefaultMethodRequestMatcher(new Method[] {});
  }

  @Test
  public void onlyAcceptsOneMethod() {

    final MethodRequestMatcher matcher =
        new DefaultMethodRequestMatcher(GET);
    assertThat(matcher.matches(requestBuilder.method(GET).build()), is(true));
    assertThat(matcher.matches(requestBuilder.method(POST).build()), is(false));
  }

  @Test
  public void acceptSeveralMethods() {
    final MethodRequestMatcher matcher = new DefaultMethodRequestMatcher(GET, POST, PATCH);
    assertThat(matcher.matches(requestBuilder.method(GET).build()), is(true));
    assertThat(matcher.matches(requestBuilder.method(POST).build()), is(true));
    assertThat(matcher.matches(requestBuilder.method(PATCH).build()), is(true));
    assertThat(matcher.matches(requestBuilder.method(OPTIONS).build()), is(false));
  }
}
