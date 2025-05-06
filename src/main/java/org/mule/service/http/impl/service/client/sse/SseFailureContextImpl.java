/*
 * Copyright 2023 Salesforce, Inc. All rights reserved.
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.service.http.impl.service.client.sse;

import org.mule.runtime.http.api.domain.message.response.HttpResponse;
import org.mule.runtime.http.api.sse.client.SseFailureContext;

/**
 * Implementation of {@link SseFailureContext} that delegates to {@link RetryHelper} the retry-related stuff.
 */
public class SseFailureContextImpl implements SseFailureContext {

  private final HttpResponse response;
  private final Throwable error;
  private final RetryHelper retryHelper;

  public SseFailureContextImpl(HttpResponse response, Throwable error, RetryHelper retryHelper) {
    this.response = response;
    this.error = error;
    this.retryHelper = retryHelper;
  }

  @Override
  public Throwable error() {
    return error;
  }

  @Override
  public HttpResponse response() {
    return response;
  }

  @Override
  public void stopRetrying() {
    retryHelper.setRetryEnabled(false);
  }
}
