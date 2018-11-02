/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.service.http.impl.service.server.grizzly;

import static java.lang.String.valueOf;
import static java.nio.charset.Charset.defaultCharset;
import static org.glassfish.grizzly.http.util.HttpStatus.CONINTUE_100;
import static org.glassfish.grizzly.http.util.HttpStatus.EXPECTATION_FAILED_417;
import static org.glassfish.grizzly.http.util.HttpStatus.SERVICE_UNAVAILABLE_503;
import static org.glassfish.grizzly.memory.Buffers.wrap;
import static org.mule.runtime.api.metadata.MediaType.TEXT;
import static org.mule.runtime.http.api.HttpConstants.Method.HEAD;
import static org.mule.runtime.http.api.HttpConstants.Protocol.HTTP;
import static org.mule.runtime.http.api.HttpConstants.Protocol.HTTPS;
import static org.mule.runtime.http.api.HttpHeaders.Names.CONTENT_LENGTH;
import static org.mule.runtime.http.api.HttpHeaders.Names.CONTENT_TYPE;
import static org.mule.runtime.http.api.HttpHeaders.Names.EXPECT;
import static org.mule.runtime.http.api.HttpHeaders.Values.CONTINUE;
import static org.mule.service.http.impl.service.server.grizzly.MuleSslFilter.SSL_SESSION_ATTRIBUTE_KEY;

import org.mule.runtime.http.api.domain.entity.EmptyHttpEntity;
import org.mule.runtime.http.api.domain.message.response.HttpResponse;
import org.mule.runtime.http.api.domain.message.response.HttpResponseBuilder;
import org.mule.runtime.http.api.domain.request.ServerConnection;
import org.mule.runtime.http.api.server.RequestHandler;
import org.mule.runtime.http.api.server.ServerAddress;
import org.mule.runtime.http.api.server.async.HttpResponseReadyCallback;
import org.mule.runtime.http.api.server.async.ResponseStatusCallback;
import org.mule.service.http.impl.service.server.DefaultServerAddress;
import org.mule.service.http.impl.service.server.RequestHandlerProvider;

import org.glassfish.grizzly.filterchain.BaseFilter;
import org.glassfish.grizzly.filterchain.FilterChainContext;
import org.glassfish.grizzly.filterchain.FilterChainEvent;
import org.glassfish.grizzly.filterchain.NextAction;
import org.glassfish.grizzly.http.HttpContent;
import org.glassfish.grizzly.http.HttpEvents.IncomingHttpUpgradeEvent;
import org.glassfish.grizzly.http.HttpEvents.OutgoingHttpUpgradeEvent;
import org.glassfish.grizzly.http.HttpHeader;
import org.glassfish.grizzly.http.HttpRequestPacket;
import org.glassfish.grizzly.http.HttpResponsePacket;

import java.io.IOException;
import java.io.Writer;
import java.net.InetSocketAddress;
import java.nio.charset.Charset;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

import javax.net.ssl.SSLSession;

/**
 * Grizzly filter that dispatches the request to the right request handler
 */
public class GrizzlyRequestDispatcherFilter extends BaseFilter {

  private final RequestHandlerProvider requestHandlerProvider;

  private final byte[] SERVER_NOT_AVAILABLE_CONTENT = ("Server not available to handle this request, either not initialized yet "
      + "or it has been disposed.").getBytes(defaultCharset());

  private ConcurrentMap<ServerAddress, AtomicInteger> activeRequests = new ConcurrentHashMap<>();

  GrizzlyRequestDispatcherFilter(final RequestHandlerProvider requestHandlerProvider) {
    this.requestHandlerProvider = requestHandlerProvider;
  }

  @Override
  public NextAction handleRead(final FilterChainContext ctx) throws IOException {
    InetSocketAddress localAddress = (InetSocketAddress) ctx.getConnection().getLocalAddress();
    DefaultServerAddress serverAddress = new DefaultServerAddress(localAddress.getAddress(), localAddress.getPort());

    AtomicInteger serverCounter = activeRequests.computeIfAbsent(serverAddress, sa -> new AtomicInteger());
    serverCounter.incrementAndGet();
    try {
      if (ctx.getMessage() instanceof HttpContent) {
        final HttpContent httpContent = ctx.getMessage();
        final HttpRequestPacket request = (HttpRequestPacket) httpContent.getHttpHeader();

        // Handle server disposal or initialize (async reconnection)
        if (!requestHandlerProvider.hasHandlerFor(serverAddress)) {
          final HttpResponsePacket.Builder responsePacketBuilder = HttpResponsePacket.builder(request);
          responsePacketBuilder.status(SERVICE_UNAVAILABLE_503.getStatusCode());
          responsePacketBuilder.reasonPhrase(SERVICE_UNAVAILABLE_503.getReasonPhrase());

          responsePacketBuilder.header(CONTENT_TYPE, TEXT.withCharset(defaultCharset()).toRfcString());
          responsePacketBuilder.header(CONTENT_LENGTH, valueOf(SERVER_NOT_AVAILABLE_CONTENT.length));

          ctx.write(HttpContent.builder(responsePacketBuilder.build())
              .content(wrap(ctx.getMemoryManager(), SERVER_NOT_AVAILABLE_CONTENT))
              .build());
          return ctx.getStopAction();
        }

        // Handle Expect Continue
        if (request.requiresAcknowledgement()) {
          final HttpResponsePacket.Builder responsePacketBuilder = HttpResponsePacket.builder(request);
          if (CONTINUE.equalsIgnoreCase(request.getHeader(EXPECT))) {
            responsePacketBuilder.status(CONINTUE_100.getStatusCode());
            responsePacketBuilder.reasonPhrase(CONINTUE_100.getReasonPhrase());
            HttpResponsePacket packet = responsePacketBuilder.build();
            packet.setAcknowledgement(true);
            ctx.write(packet);
            return ctx.getStopAction();
          } else {
            responsePacketBuilder.status(EXPECTATION_FAILED_417.getStatusCode());
            responsePacketBuilder.reasonPhrase(EXPECTATION_FAILED_417.getReasonPhrase());
            responsePacketBuilder.header(CONTENT_LENGTH, "0");
            ctx.write(responsePacketBuilder.build());
            return ctx.getStopAction();
          }
        }

        final GrizzlyHttpRequestAdapter httpRequest = new GrizzlyHttpRequestAdapter(ctx, httpContent, localAddress);
        DefaultHttpRequestContext requestContext =
            createRequestContext(ctx, (ctx.getAttributes().getAttribute(HTTPS.getScheme()) == null) ? HTTP.getScheme()
                : HTTPS.getScheme(), httpRequest);
        final RequestHandler requestHandler = requestHandlerProvider.getRequestHandler(serverAddress, httpRequest);
        requestHandler.handleRequest(requestContext, new HttpResponseReadyCallback() {

          @Override
          public void responseReady(HttpResponse response, ResponseStatusCallback responseStatusCallback) {
            try {
              if (httpRequest.getMethod().equals(HEAD.name())) {
                if (response.getEntity().isStreaming()) {
                  response.getEntity().getContent().close();
                }
                response = new HttpResponseBuilder(response).entity(new EmptyHttpEntity()).build();
              }
              if (response.getEntity().isStreaming()) {
                new ResponseStreamingCompletionHandler(ctx, requestHandler.getContextClassLoader(), request, response,
                                                       responseStatusCallback).start();
              } else {
                new ResponseCompletionHandler(ctx, requestHandler.getContextClassLoader(), request, response,
                                              responseStatusCallback).start();
              }
            } catch (Exception e) {
              responseStatusCallback.responseSendFailure(e);
            }
          }

          @Override
          public Writer startResponse(HttpResponse response, ResponseStatusCallback responseStatusCallback, Charset encoding) {
            ResponseDelayedCompletionHandler responseCompletionHandler =
                new ResponseDelayedCompletionHandler(ctx, requestHandler.getContextClassLoader(), request, response,
                                                     responseStatusCallback);
            responseCompletionHandler.start();
            return responseCompletionHandler.buildWriter(encoding);
          }
        });
        return ctx.getSuspendAction();
      } else {
        return ctx.getInvokeAction();
      }
    } finally {
      serverCounter.decrementAndGet();
    }
  }

  private DefaultHttpRequestContext createRequestContext(FilterChainContext ctx, String scheme,
                                                         GrizzlyHttpRequestAdapter httpRequest) {
    DefaultClientConnection clientConnection;
    SSLSession sslSession = (SSLSession) ctx.getAttributes().getAttribute(SSL_SESSION_ATTRIBUTE_KEY);
    if (sslSession != null) {
      clientConnection = new DefaultClientConnection(sslSession, (InetSocketAddress) ctx.getConnection().getPeerAddress());
    } else {
      clientConnection = new DefaultClientConnection((InetSocketAddress) ctx.getConnection().getPeerAddress());
    }
    ServerConnection serverConnection = new DefaultServerConnection((InetSocketAddress) ctx.getConnection().getLocalAddress());
    return new DefaultHttpRequestContext(scheme, httpRequest, clientConnection, serverConnection);
  }

  public int activeRequestsFor(ServerAddress serverAddress) {
    AtomicInteger addressActiveRequests = activeRequests.get(serverAddress);
    return addressActiveRequests == null ? 0 : addressActiveRequests.get();
  }

  @Override
  public NextAction handleEvent(FilterChainContext ctx, FilterChainEvent event) throws IOException {
    if (event.type() == IncomingHttpUpgradeEvent.TYPE) {
      final HttpHeader header = ((IncomingHttpUpgradeEvent) event).getHttpHeader();
      if (header.isRequest()) {
        // This replicates the HTTP2 handling in Grizzly
        header.setIgnoreContentModifiers(false);

        return ctx.getStopAction();
      }
    }

    if (event.type() == OutgoingHttpUpgradeEvent.TYPE) {
      final OutgoingHttpUpgradeEvent outUpgradeEvent =
          (OutgoingHttpUpgradeEvent) event;
      // If it's HTTP2 outgoing upgrade message - we have to re-enable content modifiers control
      // as the HTTP2 filters are not enabled.
      outUpgradeEvent.getHttpHeader().setIgnoreContentModifiers(false);

      return ctx.getStopAction();
    }

    return super.handleEvent(ctx, event);
  }

}
