/*
 * Copyright 2023 Salesforce, Inc. All rights reserved.
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.service.http.impl.service.message.sse;

import static java.util.Objects.requireNonNull;
import static java.util.Optional.ofNullable;

import org.mule.runtime.http.api.sse.ServerSentEvent;

import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;
import java.util.Optional;

/**
 * Server-sent event implementation.
 */
public class ServerSentEventImpl implements Serializable, ServerSentEvent {

  @Serial
  private static final long serialVersionUID = 4481268333931603096L;

  private final String eventName;
  private final String eventData;
  private final String id;
  private final Long retryDelay;

  public ServerSentEventImpl(String eventName, String eventData, String id, Long retryDelay) {
    requireNonNull(eventName, "eventName cannot be null");
    requireNonNull(eventData, "eventData cannot be null");

    this.eventName = eventName;
    this.eventData = eventData;
    this.id = id;
    this.retryDelay = retryDelay;
  }

  @Override
  public String getName() {
    return eventName;
  }

  @Override
  public String getData() {
    return eventData;
  }

  @Override
  public Optional<String> getId() {
    return ofNullable(id);
  }

  @Override
  public Optional<Long> getRetryDelay() {
    return ofNullable(retryDelay);
  }

  @Override
  public String toString() {
    return "ServerSentEvent [name=" + eventName + ", data=" + eventData + ", id=" + id + ", retryDelay=" + retryDelay + "]";
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(eventName) + Objects.hashCode(eventData) + Objects.hashCode(id) + Objects.hashCode(retryDelay);
  }

  @Override
  public boolean equals(Object o) {
    if (null == o || getClass() != o.getClass()) {
      return false;
    }
    ServerSentEventImpl that = (ServerSentEventImpl) o;
    return Objects.equals(eventName, that.eventName) && Objects.equals(eventData, that.eventData) && Objects.equals(id, that.id)
        && Objects.equals(retryDelay, that.retryDelay);
  }
}
