/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.service.http.impl.service.server.grizzly.ws;

import static java.lang.System.arraycopy;
import static java.util.Collections.unmodifiableList;
import static org.mule.runtime.api.metadata.MediaTypeUtils.isStringRepresentable;
import static org.mule.runtime.http.api.ws.WebSocket.WebSocketType.INBOUND;
import static org.mule.service.http.impl.service.ws.WebSocketUtils.asVoid;
import static org.mule.service.http.impl.service.ws.WebSocketUtils.streamDataFrame;
import org.mule.runtime.api.metadata.MediaType;
import org.mule.runtime.http.api.server.ws.WebSocketHandler;
import org.mule.runtime.http.api.ws.FragmentHandler;
import org.mule.runtime.http.api.ws.WebSocket;
import org.mule.runtime.http.api.ws.WebSocketCloseCode;
import org.mule.runtime.http.api.ws.WebSocketProtocol;
import org.mule.runtime.http.api.ws.WebSocketRequest;
import org.mule.service.http.impl.service.ws.DataFrameEmitter;
import org.mule.service.http.impl.service.ws.FragmentHandlerProvider;
import org.mule.service.http.impl.service.ws.PipedFragmentHandlerProvider;

import java.io.InputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import org.glassfish.grizzly.http.HttpRequestPacket;
import org.glassfish.grizzly.websockets.DefaultWebSocket;
import org.glassfish.grizzly.websockets.ProtocolHandler;
import org.glassfish.grizzly.websockets.WebSocketListener;

public class InboundWebSocket extends DefaultWebSocket implements WebSocket {

  private final String id;
  private final WebSocketHandler resource;
  private final WebSocketRequest request;
  private final Set<String> groups = new HashSet<>();
  private final FragmentHandlerProvider fragmentHandlerProvider;

  public InboundWebSocket(String id,
                          WebSocketHandler resource,
                          WebSocketRequest request,
                          ProtocolHandler protocolHandler,
                          HttpRequestPacket requestPacket,
                          WebSocketListener... listeners) {
    super(protocolHandler, requestPacket, listeners);
    this.id = id;
    this.resource = resource;
    this.request = request;
    fragmentHandlerProvider = new PipedFragmentHandlerProvider(id);
  }

  @Override
  public CompletableFuture<Void> send(InputStream content, MediaType mediaType) {
    DataFrameEmitter emitter = isStringRepresentable(mediaType)
        ? textEmitter()
        : binaryEmitter();

    return streamDataFrame(content, emitter);
  }

  public FragmentHandler getFragmentHandler(Consumer<FragmentHandler> newFragmentHandlerCallback) {
    return fragmentHandlerProvider.getFragmentHandler(newFragmentHandlerCallback);
  }

  private DataFrameEmitter textEmitter() {
    return new DataFrameEmitter() {

      @Override
      public CompletableFuture<Void> stream(byte[] bytes, int offset, int len, boolean last) {
        return asVoid(InboundWebSocket.this.stream(last, new String(bytes, 0, len)));
      }

      @Override
      public CompletableFuture<Void> send(byte[] bytes, int offset, int len) {
        return asVoid(InboundWebSocket.this.send(new String(bytes, 0, len)));
      }
    };
  }

  private DataFrameEmitter binaryEmitter() {
    return new DataFrameEmitter() {

      @Override
      public CompletableFuture<Void> stream(byte[] bytes, int offset, int len, boolean last) {
        return asVoid(InboundWebSocket.this.stream(last, bytes, offset, len));
      }

      @Override
      public CompletableFuture<Void> send(byte[] bytes, int offset, int len) {
        if (offset != 0 || len != bytes.length) {
          byte[] aux = new byte[len];
          arraycopy(bytes, offset, aux, 0, len);
          bytes = aux;
        }
        return asVoid(InboundWebSocket.this.send(bytes));
      }
    };
  }

  @Override
  public List<String> getGroups() {
    synchronized (groups) {
      return unmodifiableList(new ArrayList<>(groups));
    }
  }

  @Override
  public void addGroup(String group) {
    synchronized (groups) {
      groups.add(group);
    }
  }

  @Override
  public void removeGroup(String group) {
    synchronized (groups) {
      groups.remove(group);
    }
  }

  @Override
  public CompletableFuture<Void> close(WebSocketCloseCode code, String reason) {
    return asVoid(completableClose(code.getProtocolCode(), reason));
  }

  public String getId() {
    return id;
  }

  @Override
  public URI getUri() {
    return request.getRequestUri();
  }

  @Override
  public WebSocketType getType() {
    return INBOUND;
  }

  @Override
  public WebSocketProtocol getProtocol() {
    return request.getScheme();
  }

  public WebSocketRequest getRequest() {
    return request;
  }

  public WebSocketHandler getResource() {
    return resource;
  }

  @Override
  public String toString() {
    return "Connection Id: " + id + "\nType: " + INBOUND + "\nURI: " + getUri();
  }
}
