/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.service.http.impl.service.server.grizzly.ws;

import static java.lang.String.format;
import static org.glassfish.grizzly.websockets.WebSocket.ABNORMAL_CLOSE;
import static org.glassfish.grizzly.websockets.WebSocketEngine.getEngine;
import static org.mule.runtime.api.metadata.MediaType.ANY;
import static org.mule.runtime.api.metadata.MediaType.BINARY;
import static org.mule.runtime.api.metadata.MediaType.TEXT;
import static org.mule.runtime.http.api.ws.WebSocketCloseCode.ENDPOINT_GOING_DOWN;
import static org.mule.runtime.http.api.ws.WebSocketCloseCode.fromProtocolCode;
import static org.slf4j.LoggerFactory.getLogger;
import org.mule.runtime.api.metadata.DataType;
import org.mule.runtime.api.metadata.MediaType;
import org.mule.runtime.api.metadata.TypedValue;
import org.mule.runtime.http.api.server.ws.WebSocketConnectionRejectedException;
import org.mule.runtime.http.api.server.ws.WebSocketHandler;
import org.mule.runtime.http.api.server.ws.WebSocketHandlerManager;
import org.mule.runtime.http.api.ws.FragmentHandler;
import org.mule.runtime.http.api.ws.WebSocketMessage;
import org.mule.runtime.http.api.ws.WebSocketRequest;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

import org.glassfish.grizzly.http.HttpRequestPacket;
import org.glassfish.grizzly.websockets.ClosingFrame;
import org.glassfish.grizzly.websockets.DataFrame;
import org.glassfish.grizzly.websockets.ProtocolHandler;
import org.glassfish.grizzly.websockets.WebSocketApplication;
import org.glassfish.grizzly.websockets.WebSocketListener;
import org.slf4j.Logger;

public class GrizzlyWebSocketHandlerManager extends WebSocketApplication implements WebSocketHandlerManager {

  private static final Logger LOGGER = getLogger(GrizzlyWebSocketHandlerManager.class);

  private final WebSocketHandler handler;
  private boolean stopped = true;

  public GrizzlyWebSocketHandlerManager(WebSocketHandler handler) {
    this.handler = handler;
  }

  @Override
  public void start() {
    getEngine().register("", handler.getPath(), this);
    stopped = false;
  }

  @Override
  public void stop() {
    stopped = true;
    try {
      getEngine().unregister(this);
    } finally {
      sockets.keySet().forEach(s -> {
        InboundWebSocket socket = (InboundWebSocket) s;
        socket.close(ENDPOINT_GOING_DOWN, "Endpoint stopped").whenComplete((v, e) -> {
          if (LOGGER.isWarnEnabled()) {
            LOGGER.warn(format(
                "Failed to close socket '%s' while stopping its endpoint: %s", socket.getId(), e.getMessage()), e);
          }
        });

      });
    }
  }

  @Override
  public void dispose() {

  }

  @Override
  public org.glassfish.grizzly.websockets.WebSocket createSocket(ProtocolHandler handler,
                                                                 HttpRequestPacket requestPacket,
                                                                 WebSocketListener... listeners) {

    final GrizzlyInboundWebSocketRequest request = new GrizzlyInboundWebSocketRequest(requestPacket);
    String id = this.handler.getConnectionHandler().getSocketId(request);
    return new InboundWebSocket(id, this.handler, request, handler, requestPacket, listeners);
  }

  @Override
  public void onConnect(org.glassfish.grizzly.websockets.WebSocket ws) {
    if (stopped) {
      throw new IllegalStateException("Endpoint is stopped");
    }
    
    super.onConnect(ws);
    InboundWebSocket socket = (InboundWebSocket) ws;
    try {
      socket.getResource().getConnectionHandler().onConnect(socket, socket.getRequest());
    } catch (WebSocketConnectionRejectedException e) {
      if (LOGGER.isInfoEnabled()) {
        LOGGER.info(format("Closing Inbound WebSocket '%s' due to exception thrown by onConnect() handler: %s",
                           socket.getId(), e.getMessage()),
                    e);

      }
      ws.close(ABNORMAL_CLOSE, e.getMessage());
    }
  }

  @Override
  public void onClose(org.glassfish.grizzly.websockets.WebSocket ws, DataFrame frame) {
    super.onClose(ws, frame);
    InboundWebSocket socket = (InboundWebSocket) ws;
    ClosingFrame closing = (ClosingFrame) frame;
    ((InboundWebSocket) ws).getResource().getConnectionHandler().onClose(socket,
                                                                         socket.getRequest(),
                                                                         fromProtocolCode(closing.getCode()),
                                                                         closing.getReason());
  }

  @Override
  public void onMessage(org.glassfish.grizzly.websockets.WebSocket ws, String text) {
    InboundWebSocket socket = (InboundWebSocket) ws;
    socket.getResource().getMessageHandler().onMessage(buildMessage(socket, text));
  }

  @Override
  public void onMessage(org.glassfish.grizzly.websockets.WebSocket ws, byte[] bytes) {
    InboundWebSocket socket = (InboundWebSocket) ws;
    socket.getResource().getMessageHandler().onMessage(buildMessage(socket, bytes));
  }

  @Override
  public void onFragment(org.glassfish.grizzly.websockets.WebSocket socket, byte[] fragment, boolean last) {
    onFragment((InboundWebSocket) socket, fragment, BINARY, last);
  }

  @Override
  public void onFragment(org.glassfish.grizzly.websockets.WebSocket socket, String fragment, boolean last) {
    onFragment((InboundWebSocket) socket, fragment.getBytes(), TEXT, last);
  }

  private void onFragment(InboundWebSocket socket, byte[] data, MediaType mediaType, boolean last) {
    FragmentHandler handler = socket.getFragmentHandler(h -> socket.getResource().getMessageHandler()
        .onMessage(buildMessage(socket, h.getInputStream(), mediaType)));

    try {
      if (!handler.write(data)) {
        if (LOGGER.isDebugEnabled()) {
          LOGGER.debug("Incoming fragment for socket '{}' was discarded because the stream was already closed");
        }
      }
    } catch (IOException e) {
      LOGGER.error(
          format("Error found while streaming data on socket %s: %s. Stream will be closed", socket.getId(), e.getMessage()), e);
      handler.abort();
      return;
    }

    if (last) {
      handler.complete();
    }
  }

  private WebSocketMessage buildMessage(InboundWebSocket socket, String text) {
    return buildMessage(socket, new ByteArrayInputStream(text.getBytes()), TEXT);
  }

  private WebSocketMessage buildMessage(InboundWebSocket socket, byte[] bytes) {
    return buildMessage(socket, new ByteArrayInputStream(bytes), BINARY);
  }

  private WebSocketMessage buildMessage(InboundWebSocket socket, InputStream stream, MediaType mediaType) {
    return new DefaultWebSocketMessage(socket, new TypedValue<>(stream, DataType.builder()
        .type(InputStream.class)
        .mediaType(resolveMediaType(socket.getRequest(), mediaType))
        .build()), socket.getRequest());
  }

  private MediaType resolveMediaType(WebSocketRequest request, MediaType defaultMediaType) {
    MediaType resolved = request.getContentType();
    if (resolved == ANY) {
      resolved = defaultMediaType;
    }

    return resolved;
  }
}
