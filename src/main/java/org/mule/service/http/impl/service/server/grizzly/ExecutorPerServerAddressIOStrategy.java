/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.service.http.impl.service.server.grizzly;

import static java.util.EnumSet.of;
import static org.glassfish.grizzly.IOEvent.WRITE;
import org.mule.runtime.http.api.server.ServerAddress;
import org.mule.service.http.impl.service.server.DefaultServerAddress;

import org.glassfish.grizzly.Connection;
import org.glassfish.grizzly.Grizzly;
import org.glassfish.grizzly.IOEvent;
import org.glassfish.grizzly.IOEventLifeCycleListener;
import org.glassfish.grizzly.strategies.AbstractIOStrategy;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.EnumSet;
import java.util.concurrent.Executor;
import java.util.logging.Logger;

/**
 * Grizzly IO Strategy that will handle each work to an specific {@link Executor} based on the
 * {@link ServerAddress} of a {@link Connection}.
 * <p/>
 * There's logic from {@link org.glassfish.grizzly.strategies.WorkerThreadIOStrategy} that need to be reused but unfortunately
 * that class cannot be override.
 */
public class ExecutorPerServerAddressIOStrategy extends AbstractIOStrategy {

  private final static EnumSet<IOEvent> WORKER_THREAD_EVENT_SET = of(WRITE);
  public static final String DELEGATE_WRITES_IN_CONFIGURED_EXECUTOR = "__WRITES_TO_IO__";

  private static final Logger logger = Grizzly.logger(ExecutorPerServerAddressIOStrategy.class);
  private final ExecutorProvider executorProvider;

  public ExecutorPerServerAddressIOStrategy(final ExecutorProvider executorProvider) {
    this.executorProvider = executorProvider;
  }

  @Override
  public boolean executeIoEvent(final Connection connection, final IOEvent ioEvent, final boolean isIoEventEnabled)
      throws IOException {

    final boolean isReadOrWriteEvent = isReadWrite(ioEvent);

    final IOEventLifeCycleListener listener;
    if (isReadOrWriteEvent) {
      if (isIoEventEnabled) {
        connection.disableIOEvent(ioEvent);
      }

      listener = ENABLE_INTEREST_LIFECYCLE_LISTENER;
    } else {
      listener = null;
    }

    final Executor threadPool = getThreadPoolFor(connection, ioEvent);
    if (threadPool != null) {
      threadPool.execute(new WorkerThreadRunnable(connection, ioEvent, listener));
    } else {
      run0(connection, ioEvent, listener);
    }

    return true;
  }

  @Override
  public Executor getThreadPoolFor(Connection connection, IOEvent ioEvent) {
    if (mustSwitchThread(connection, ioEvent)) {
      final InetSocketAddress socketAddress = (InetSocketAddress) connection.getLocalAddress();
      final InetAddress address = socketAddress.getAddress();
      final int port = socketAddress.getPort();
      return executorProvider.getExecutor(new DefaultServerAddress(address, port));
    } else {
      return null;
    }
  }

  private boolean mustSwitchThread(Connection connection, IOEvent ioEvent) {
    Object delegateToConfigured = connection.getAttributes().getAttribute(DELEGATE_WRITES_IN_CONFIGURED_EXECUTOR);
    if (!(delegateToConfigured instanceof Boolean)) {
      return false;
    }
    if (!(Boolean) delegateToConfigured) {
      return false;
    }
    return WORKER_THREAD_EVENT_SET.contains(ioEvent);
  }

  private static void run0(final Connection connection, final IOEvent ioEvent, final IOEventLifeCycleListener lifeCycleListener) {
    fireIOEvent(connection, ioEvent, lifeCycleListener, logger);
  }

  private static final class WorkerThreadRunnable implements Runnable {

    final Connection connection;
    final IOEvent ioEvent;
    final IOEventLifeCycleListener lifeCycleListener;

    private WorkerThreadRunnable(final Connection connection, final IOEvent ioEvent,
                                 final IOEventLifeCycleListener lifeCycleListener) {
      this.connection = connection;
      this.ioEvent = ioEvent;
      this.lifeCycleListener = lifeCycleListener;
    }

    @Override
    public void run() {
      run0(connection, ioEvent, lifeCycleListener);
    }
  }

}
