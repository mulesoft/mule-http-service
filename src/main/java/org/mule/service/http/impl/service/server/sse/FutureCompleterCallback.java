/*
 * Copyright 2023 Salesforce, Inc. All rights reserved.
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.service.http.impl.service.server.sse;

import org.mule.runtime.http.api.server.async.ResponseStatusCallback;

import java.util.concurrent.CompletableFuture;

/**
 * Implementation of {@link ResponseStatusCallback} that delegates to a {@link CompletableFuture<Void>}.
 */
public class FutureCompleterCallback implements ResponseStatusCallback {

  private final CompletableFuture<Void> future;

  public FutureCompleterCallback(CompletableFuture<Void> future) {
    this.future = future;
  }

  @Override
  public void responseSendFailure(Throwable throwable) {
    future.completeExceptionally(throwable);
  }

  @Override
  public void responseSendSuccessfully() {
    future.complete(null);
  }

  @Override
  public void onErrorSendingResponse(Throwable throwable) {
    future.completeExceptionally(throwable);
  }
}
