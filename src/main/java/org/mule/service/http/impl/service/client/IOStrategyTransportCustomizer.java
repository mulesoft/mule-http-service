/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.service.http.impl.service.client;

import com.ning.http.client.providers.grizzly.TransportCustomizer;

import java.util.concurrent.ExecutorService;

import org.glassfish.grizzly.filterchain.FilterChainBuilder;
import org.glassfish.grizzly.nio.transport.TCPNIOTransport;
import org.glassfish.grizzly.strategies.SameThreadIOStrategy;
import org.glassfish.grizzly.strategies.WorkerThreadIOStrategy;

/**
 * Transport customizer that sets the IO strategy to {@code SameThreadIOStrategy} and the thread pool for the NIO transport
 * selector.
 */
public class IOStrategyTransportCustomizer implements TransportCustomizer {

  private final boolean streamingEnabled;
  private ExecutorService selectorPool;
  private ExecutorService workerPool;
  private int selectorCount;

  public IOStrategyTransportCustomizer(ExecutorService selectorPool, ExecutorService workerPool, boolean streamingEnabled,
                                       int selectorCount) {
    this.selectorPool = selectorPool;
    this.workerPool = workerPool;
    this.streamingEnabled = streamingEnabled;
    this.selectorCount = selectorCount;
  }

  @Override
  public void customize(TCPNIOTransport transport, FilterChainBuilder filterChainBuilder) {
    // When streaming is enabled, a larger pool is required to handle large responses that take many iterations to process
    transport.setIOStrategy(streamingEnabled ? WorkerThreadIOStrategy.getInstance() : SameThreadIOStrategy.getInstance());
    transport.setKernelThreadPool(selectorPool);
    transport.setWorkerThreadPool(workerPool);
    transport.setSelectorRunnersCount(selectorCount);
  }
}
