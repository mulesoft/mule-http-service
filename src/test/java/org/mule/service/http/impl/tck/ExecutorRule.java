/*
 * Copyright 2023 Salesforce, Inc. All rights reserved.
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.service.http.impl.tck;

import static java.util.concurrent.Executors.newCachedThreadPool;

import java.util.concurrent.ExecutorService;

import org.junit.rules.ExternalResource;

public class ExecutorRule extends ExternalResource {

  private ExecutorService executor;

  @Override
  protected void before() throws Throwable {
    executor = newCachedThreadPool();
  }

  @Override
  protected void after() {
    executor.shutdownNow();
  }

  public ExecutorService getExecutor() {
    return executor;
  }
}
