/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.service.http.impl.service.client;

import static java.lang.Boolean.parseBoolean;
import static java.lang.System.getProperty;
import static org.mule.runtime.api.util.MuleSystemProperties.FORCE_WORKER_THREAD_IO_STRATEGY_WHEN_TLS_ENABLED;

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

  private static final boolean USE_WORKER_THREAD_IO_STRATEGY_WHEN_TLS_ENABLED =
      parseBoolean(getProperty(FORCE_WORKER_THREAD_IO_STRATEGY_WHEN_TLS_ENABLED));

  private final boolean streamingEnabled;
  private ExecutorService selectorPool;
  private ExecutorService workerPool;
  private int selectorCount;
  private final boolean tlsEnabled;

  public IOStrategyTransportCustomizer(ExecutorService selectorPool, ExecutorService workerPool, boolean streamingEnabled,
                                       int selectorCount, boolean tlsEnabled) {
    this.selectorPool = selectorPool;
    this.workerPool = workerPool;
    this.streamingEnabled = streamingEnabled;
    this.selectorCount = selectorCount;
    this.tlsEnabled = tlsEnabled;
  }

  @Override
  public void customize(TCPNIOTransport transport, FilterChainBuilder filterChainBuilder) {
    boolean forceWorkerThreadStrategy = USE_WORKER_THREAD_IO_STRATEGY_WHEN_TLS_ENABLED && tlsEnabled;
    // When streaming is enabled, a larger pool is required to handle large responses that take many iterations to process
    transport.setIOStrategy(streamingEnabled || forceWorkerThreadStrategy ? WorkerThreadIOStrategy.getInstance()
        : SameThreadIOStrategy.getInstance());
    transport.setKernelThreadPool(selectorPool);
    transport.setWorkerThreadPool(workerPool);
    transport.setSelectorRunnersCount(selectorCount);
  }
}
