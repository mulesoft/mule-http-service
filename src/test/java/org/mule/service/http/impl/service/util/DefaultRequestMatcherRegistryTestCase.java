/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.service.http.impl.service.util;

import static java.lang.String.format;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.Assert.assertThat;
import static org.mule.runtime.http.api.HttpConstants.Method.GET;
import static org.mule.runtime.http.api.HttpConstants.Method.POST;
import static org.mule.service.http.impl.AllureConstants.HttpFeature.HTTP_SERVICE;
import org.mule.runtime.http.api.HttpConstants.Method;
import org.mule.runtime.http.api.domain.message.request.HttpRequest;
import org.mule.runtime.http.api.server.MethodRequestMatcher;
import org.mule.runtime.http.api.server.PathAndMethodRequestMatcher;
import org.mule.runtime.http.api.utils.RequestMatcherRegistry;
import org.mule.tck.junit4.AbstractMuleTestCase;

import io.qameta.allure.Feature;
import org.hamcrest.Matcher;
import org.junit.Test;

@Feature(HTTP_SERVICE)
public class DefaultRequestMatcherRegistryTestCase extends AbstractMuleTestCase {

  private static final Object METHOD_MISMATCH = new Object();
  private static final Object NOT_FOUND = new Object();
  private static final Object DISABLED = new Object();
  private static final Object SECOND_LEVEL_CATCH_ALL = new Object();
  private static final Object SECOND_LEVEL_SPECIFIC = new Object();
  private static final Object FIRST_LEVEL_SPECIFIC = new Object();

  @Test
  public void findByRequest() {
    RequestMatcherRegistry registry = buildRegistry(getFullBuilder());
    validateRequestMatch(registry, "/path/somewhere", SECOND_LEVEL_CATCH_ALL);
    validateRequestMatch(registry, "/path/here", SECOND_LEVEL_SPECIFIC);
    validateRequestMatch(registry, "/here", DISABLED);
    validateRequestMatch(registry, "/nope", NOT_FOUND);
    validateRequestMatch(registry, "/path/somewhere", sameInstance(METHOD_MISMATCH), POST);
  }

  @Test
  public void findsByMethodAndPath() {
    RequestMatcherRegistry registry = buildRegistry(getFullBuilder());
    validateMethodAndPathMatch(registry, "/path/somewhere", SECOND_LEVEL_CATCH_ALL);
    validateMethodAndPathMatch(registry, "/path/here", SECOND_LEVEL_SPECIFIC);
    validateMethodAndPathMatch(registry, "/here", DISABLED);
    validateMethodAndPathMatch(registry, "/nope", NOT_FOUND);
    validateMethodAndPathMatch(registry, "/path/somewhere", sameInstance(METHOD_MISMATCH), POST);
  }

  @Test
  public void allNullIfDefault() {
    RequestMatcherRegistry registry = buildRegistry(new DefaultRequestMatcherRegistryBuilder());
    validateMethodAndPathMatch(registry, "/path/somewhere", SECOND_LEVEL_CATCH_ALL);
    validateMethodAndPathMatch(registry, "/path/here", SECOND_LEVEL_SPECIFIC);
    validateMethodAndPathMatch(registry, "/here", nullValue(), GET);
    validateMethodAndPathMatch(registry, "/nope", nullValue(), GET);
    validateMethodAndPathMatch(registry, "/path/somewhere", nullValue(), POST);
  }

  private RequestMatcherRegistry buildRegistry(RequestMatcherRegistry.RequestMatcherRegistryBuilder builder) {
    RequestMatcherRegistry<Object> registry = builder.build();
    registry.add(PathAndMethodRequestMatcher.builder()
        .methodRequestMatcher(MethodRequestMatcher.builder().add(GET).build())
        .path("/path/*")
        .build(),
                 SECOND_LEVEL_CATCH_ALL);
    registry.add(PathAndMethodRequestMatcher.builder().path("/path/here").build(), SECOND_LEVEL_SPECIFIC);
    registry.add(PathAndMethodRequestMatcher.builder().path("/here").build(), FIRST_LEVEL_SPECIFIC).disable();
    return registry;
  }

  private RequestMatcherRegistry.RequestMatcherRegistryBuilder<Object> getFullBuilder() {
    return new DefaultRequestMatcherRegistryBuilder<>()
        .onMethodMismatch(() -> METHOD_MISMATCH)
        .onNotFound(() -> NOT_FOUND)
        .onDisabled(() -> DISABLED);
  }

  private void validateRequestMatch(RequestMatcherRegistry registry, String path, Object expectedItem) {
    validateRequestMatch(registry, path, sameInstance(expectedItem), GET);
  }

  private void validateRequestMatch(RequestMatcherRegistry registry, String path, Matcher matcher, Method method) {
    assertThat(registry.find(HttpRequest.builder().uri(format("http://localhost:8081%s", path)).method(method).build()),
               is(matcher));
  }

  private void validateMethodAndPathMatch(RequestMatcherRegistry registry, String path, Object expectedItem) {
    validateMethodAndPathMatch(registry, path, sameInstance(expectedItem), GET);
  }

  private void validateMethodAndPathMatch(RequestMatcherRegistry registry, String path, Matcher matcher, Method method) {
    assertThat(registry.find(method.name(), path), is(matcher));
  }

}
