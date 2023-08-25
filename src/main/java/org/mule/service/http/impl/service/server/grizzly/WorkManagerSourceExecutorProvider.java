/*
 * Copyright 2023 Salesforce, Inc. All rights reserved.
 */
package org.mule.service.http.impl.service.server.grizzly;

import org.mule.runtime.http.api.server.ServerAddress;
import org.mule.service.http.impl.service.server.ServerAddressMap;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.function.Supplier;

/**
 * {@link ExecutorProvider} implementation that retrieves an {@link Executor} for a {@link ServerAddress}.
 */
public class WorkManagerSourceExecutorProvider implements ExecutorProvider {

  private ServerAddressMap<Supplier<ExecutorService>> executorPerServerAddress =
      new ServerAddressMap<>(new ConcurrentHashMap<>());

  /**
   * Adds an {@link Executor} to be used when a request is made to a {@link ServerAddress}
   *
   * @param serverAddress     address to which the executor should be applied to
   * @param workManagerSource the executor to use when a request is done to the server address
   */
  public void addExecutor(final ServerAddress serverAddress, final Supplier<ExecutorService> workManagerSource) {
    executorPerServerAddress.put(serverAddress, workManagerSource);
  }

  public void removeExecutor(ServerAddress serverAddress) {
    executorPerServerAddress.remove(serverAddress);
  }

  @Override
  public Executor getExecutor(ServerAddress serverAddress) {
    Supplier<ExecutorService> executorServiceSupplier = executorPerServerAddress.get(serverAddress);
    if (executorServiceSupplier == null) {
      return null;
    } else {
      return executorServiceSupplier.get();
    }
  }
}
