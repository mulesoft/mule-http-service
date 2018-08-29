/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.service.http.impl.service.ws;

import org.mule.runtime.http.api.ws.FragmentHandler;

import java.util.function.Consumer;

public class PipedFragmentHandlerProvider implements FragmentHandlerProvider {

  private final String socketId;
  private FragmentHandler fragmentHandler = null;

  public PipedFragmentHandlerProvider(String socketId) {
    this.socketId = socketId;
  }

  @Override
  public FragmentHandler getFragmentHandler(Consumer<FragmentHandler> newFragmentHandlerCallback) {
    synchronized (this) {
      if (fragmentHandler == null) {
        fragmentHandler = new PipedFragmentHandler(socketId, this::recycle);
        newFragmentHandlerCallback.accept(fragmentHandler);
      }

      return fragmentHandler;
    }
  }

  private void recycle() {
    synchronized (this) {
      fragmentHandler = null;
    }
  }
}
