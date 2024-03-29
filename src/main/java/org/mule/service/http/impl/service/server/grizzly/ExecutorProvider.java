/*
 * Copyright 2023 Salesforce, Inc. All rights reserved.
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
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
