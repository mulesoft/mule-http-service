/*
 * Copyright 2023 Salesforce, Inc. All rights reserved.
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.service.http.impl.service.client.sse;

import static java.util.concurrent.TimeUnit.MILLISECONDS;

import org.mule.runtime.http.api.sse.client.SseRetryConfig;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;

/**
 * Handles the retry-related context of a {@link DefaultSseSource}.
 */
public class RetryHelper {

  private final ScheduledExecutorService retryScheduler;
  private final SseRetryConfig retryConfig;
  private final InternalConnectable source;

  private Long retryDelayMillis;
  private boolean retryEnabled;
  private ScheduledFuture<?> retryFuture;

  public RetryHelper(ScheduledExecutorService retryScheduler, SseRetryConfig retryConfig, InternalConnectable source) {
    this.retryScheduler = retryScheduler;
    this.retryConfig = retryConfig;
    this.retryDelayMillis = retryConfig.initialRetryDelayMillis();
    this.source = source;
    this.retryEnabled = true;
  }

  public void setDelayIfAllowed(Long newDelay) {
    if (retryConfig.allowRetryDelayOverride()) {
      this.retryDelayMillis = newDelay;
    }
  }

  public boolean shouldRetryOnStreamEnd() {
    return retryEnabled && retryConfig.shouldRetryOnStreamEnd();
  }

  public void scheduleReconnection() {
    retryFuture = retryScheduler.schedule(source::internalConnect, retryDelayMillis, MILLISECONDS);
  }

  public boolean isRetryEnabled() {
    return retryEnabled;
  }

  public void setRetryEnabled(boolean retryEnabled) {
    this.retryEnabled = retryEnabled;
  }

  public void abortReties() {
    this.retryEnabled = false;
    if (null != retryFuture) {
      retryFuture.cancel(true);
    }
  }
}
