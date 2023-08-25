/*
 * Copyright 2023 Salesforce, Inc. All rights reserved.
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
