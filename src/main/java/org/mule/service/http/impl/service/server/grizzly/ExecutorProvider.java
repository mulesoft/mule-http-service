/*
 * Copyright 2023 Salesforce, Inc. All rights reserved.
 */
package org.mule.service.http.impl.service.server.grizzly;


import org.mule.runtime.http.api.server.ServerAddress;

import java.util.concurrent.Executor;

public interface ExecutorProvider {

  /**
   * Provides an {@link Executor} for a {@link ServerAddress}
   *
   * @param serverAddress an HTTP server address
   * @return the executor to use for process HTTP request for the server address
   */
  Executor getExecutor(ServerAddress serverAddress);

}
