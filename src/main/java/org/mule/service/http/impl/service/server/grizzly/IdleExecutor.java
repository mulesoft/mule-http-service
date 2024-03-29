/*
 * Copyright 2023 Salesforce, Inc. All rights reserved.
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.service.http.impl.service.server.grizzly;

import java.util.concurrent.ExecutorService;

import org.glassfish.grizzly.utils.DelayedExecutor;

/**
 * Object containing all idle check executors, making it easy to dispose them once a server goes down.
 */
public class IdleExecutor {

  public static final String IDLE_TIMEOUT_THREADS_PREFIX_NAME = ".HttpIdleConnectionCloser";

  private DelayedExecutor idleTimeoutDelayedExecutor;

  public IdleExecutor(ExecutorService idleTimeoutExecutorService) {
    this.idleTimeoutDelayedExecutor = new DelayedExecutor(idleTimeoutExecutorService);
  }

  public void start() {
    idleTimeoutDelayedExecutor.start();
  }

  public DelayedExecutor getIdleTimeoutDelayedExecutor() {
    return idleTimeoutDelayedExecutor;
  }

  public void dispose() {
    idleTimeoutDelayedExecutor.destroy();
  }

}
