/*
 * Copyright 2023 Salesforce, Inc. All rights reserved.
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.service.http.impl.service.server.sse;

import org.mule.runtime.http.api.sse.server.SseClient;
import org.mule.service.http.impl.service.message.sse.ServerSentEventImpl;
import org.mule.service.http.impl.service.message.sse.SseEntityEncoder;

import java.io.IOException;
import java.io.Writer;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Basic Grizzly implementation of {@link SseClient}.
 */
public class GrizzlySseClient implements SseClient {

  private final Writer bodyWriter;
  private final SseEntityEncoder encoder;
  private final String clientId;
  private CompletableFuture<Void> responseSentFuture;

  public GrizzlySseClient(Writer bodyWriter, String clientId, CompletableFuture<Void> responseSentFuture) {
    this.bodyWriter = bodyWriter;
    this.responseSentFuture = responseSentFuture;
    this.encoder = new SseEntityEncoder();
    this.clientId = clientId;
  }

  @Override
  public void sendEvent(String eventName, String payload, String id, Long retryDelay) throws IOException {
    encoder.writeTo(bodyWriter, new ServerSentEventImpl(eventName, payload, id, retryDelay));
  }

  @Override
  public void sendComment(String comment) {
    // TODO (W-18041205): Implement comments.
  }

  @Override
  public void onClose(Consumer<Throwable> callback) {
    responseSentFuture = responseSentFuture.whenComplete((ignored, error) -> callback.accept(error));
  }

  @Override
  public void close() throws IOException {
    bodyWriter.close();
  }

  @Override
  public String getClientId() {
    return clientId;
  }
}
