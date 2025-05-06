/*
 * Copyright 2023 Salesforce, Inc. All rights reserved.
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.service.http.impl.util.sse;

import org.mule.runtime.http.api.sse.ServerSentEvent;

import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeMatcher;

public class ServerSentEventTypeSafeMatcher extends TypeSafeMatcher<ServerSentEvent> {

  private final String name;
  private final String data;
  private final String id;
  private final Long retryDelay;

  public static Matcher<? super ServerSentEvent> aServerSentEvent(String name, String data) {
    return new ServerSentEventTypeSafeMatcher(name, data, null, null);
  }

  public static Matcher<? super ServerSentEvent> aServerSentEvent(String name, String data, String id, Long retryDelay) {
    return new ServerSentEventTypeSafeMatcher(name, data, id, retryDelay);
  }

  private ServerSentEventTypeSafeMatcher(String name, String data, String id, Long retryDelay) {
    this.name = name;
    this.data = data;
    this.id = id;
    this.retryDelay = retryDelay;
  }

  @Override
  public void describeTo(Description description) {
    description
        .appendText("A server-sent event with name '").appendValue(name).appendText("'")
        .appendText(", with data '").appendValue(data).appendText("'");

    if (id != null) {
      description.appendText(", with id '").appendValue(id).appendText("'");
    }

    if (retryDelay != null) {
      description.appendText(", with retryDelay '").appendValue(retryDelay).appendText("'");
    }
  }

  @Override
  protected boolean matchesSafely(ServerSentEvent item) {
    if (!item.getName().equals(name)) {
      return false;
    }

    if (!item.getData().equals(data)) {
      return false;
    }

    if (id != null) {
      if (!item.getId().isPresent()) {
        return false;
      }
      if (!item.getId().get().equals(id)) {
        return false;
      }
    }

    if (retryDelay != null) {
      if (!item.getRetryDelay().isPresent()) {
        return false;
      }
      if (!retryDelay.equals(item.getRetryDelay().get())) {
        return false;
      }
    }

    return true;
  }
}
