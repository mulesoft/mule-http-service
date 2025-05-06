/*
 * Copyright 2023 Salesforce, Inc. All rights reserved.
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.service.http.impl.util.sse;

import org.mule.runtime.api.util.concurrent.Latch;
import org.mule.runtime.http.api.sse.ServerSentEvent;
import org.mule.runtime.http.api.sse.client.SseListener;

import java.util.ArrayList;
import java.util.List;

public class SSEEventsAggregator implements SseListener {

  private final List<ServerSentEvent> receivedEvents;
  private final Latch closeLatch;

  public SSEEventsAggregator() {
    receivedEvents = new ArrayList<>();
    closeLatch = new Latch();
  }

  @Override
  public void onEvent(ServerSentEvent event) {
    receivedEvents.add(event);
  }

  @Override
  public void onClose() {
    closeLatch.release();
  }

  public List<ServerSentEvent> getList() throws InterruptedException {
    closeLatch.await();
    return receivedEvents;
  }
}
