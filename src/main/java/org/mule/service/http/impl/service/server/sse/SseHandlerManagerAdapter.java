/*
 * Copyright 2023 Salesforce, Inc. All rights reserved.
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.service.http.impl.service.server.sse;

import org.mule.runtime.http.api.server.RequestHandlerManager;
import org.mule.runtime.http.api.sse.server.SseEndpointManager;

public class SseHandlerManagerAdapter implements SseEndpointManager {

  private final RequestHandlerManager requestHandlerManager;

  public SseHandlerManagerAdapter(RequestHandlerManager requestHandlerManager) {
    this.requestHandlerManager = requestHandlerManager;
  }

  @Override
  public void stop() {
    requestHandlerManager.stop();
  }

  @Override
  public void start() {
    requestHandlerManager.start();
  }

  @Override
  public void dispose() {
    requestHandlerManager.dispose();
  }
}
