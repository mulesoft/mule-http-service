/*
 * Copyright 2023 Salesforce, Inc. All rights reserved.
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.service.http.impl.service.message.sse;

import org.mule.runtime.http.api.sse.ServerSentEvent;

/**
 * Builds instances of {@link ServerSentEvent}.
 */
public class SseEventBuilder {

  private String eventName;
  private String id;
  private final StringBuilder eventData;
  private Long retryDelay;

  public SseEventBuilder() {
    eventName = null;
    id = null;
    eventData = new StringBuilder();
    retryDelay = null;
  }

  public ServerSentEvent buildAndClear() {
    ServerSentEvent event = new ServerSentEventImpl(eventName, eventData.toString(), id, retryDelay);
    eventName = null;
    id = null;
    eventData.setLength(0);
    retryDelay = null;
    return event;
  }

  public SseEventBuilder withData(String dataLine) {
    if (!eventData.isEmpty()) {
      eventData.append("\n");
    }
    eventData.append(dataLine);
    return this;
  }

  public SseEventBuilder withName(String eventName) {
    this.eventName = eventName;
    return this;
  }

  public SseEventBuilder withId(String id) {
    this.id = id;
    return this;
  }

  public SseEventBuilder withRetryDelay(Long retryDelay) {
    this.retryDelay = retryDelay;
    return this;
  }
}
