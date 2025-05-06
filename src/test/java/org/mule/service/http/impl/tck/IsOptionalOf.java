/*
 * Copyright 2023 Salesforce, Inc. All rights reserved.
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.service.http.impl.tck;

import static org.hamcrest.core.Is.is;

import java.util.Optional;

import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeMatcher;

public class IsOptionalOf<T> extends TypeSafeMatcher<Optional<T>> {

  private final Matcher<T> expectedElemMatcher;

  private IsOptionalOf(Matcher<T> expectedElemMatcher) {
    this.expectedElemMatcher = expectedElemMatcher;
  }

  protected boolean matchesSafely(Optional<T> item) {
    return item.isPresent() && expectedElemMatcher.matches(item.get());
  }

  public void describeTo(Description description) {
    description.appendText("an Optional that ").appendDescriptionOf(expectedElemMatcher);
  }

  protected void describeMismatchSafely(Optional<T> item, Description mismatchDescription) {
    if (!item.isPresent()) {
      mismatchDescription.appendText("got an empty Optional");
    } else {
      mismatchDescription.appendText("got an Optional that ");
      expectedElemMatcher.describeMismatch(item.get(), mismatchDescription);
    }
  }

  public static <T> IsOptionalOf<T> isOptionalOf(Matcher<T> expectedElemMatcher) {
    return new IsOptionalOf<>(expectedElemMatcher);
  }

  public static <T> IsOptionalOf<T> isOptionalOf(T expectedValue) {
    return isOptionalOf(is(expectedValue));
  }
}
