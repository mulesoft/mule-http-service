/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.service.http.impl.service.server.grizzly.ws;

import org.mule.runtime.api.metadata.TypedValue;
import org.mule.runtime.http.api.ws.WebSocketRequest;
import org.mule.runtime.http.api.ws.WebSocket;
import org.mule.runtime.http.api.ws.WebSocketMessage;

import java.io.InputStream;

public class DefaultWebSocketMessage implements WebSocketMessage {

  private final WebSocket socket;
  private final TypedValue<InputStream> content;
  private final WebSocketRequest request;

  public DefaultWebSocketMessage(WebSocket socket, TypedValue<InputStream> content, WebSocketRequest request) {
    this.socket = socket;
    this.content = content;
    this.request = request;
  }

  @Override
  public TypedValue<InputStream> getContent() {
    return content;
  }

  @Override
  public WebSocket getSocket() {
    return socket;
  }

  @Override
  public WebSocketRequest getRequest() {
    return request;
  }
}
