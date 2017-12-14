/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.service.http.impl.service.server.grizzly;

import static org.glassfish.grizzly.http.util.HttpStatus.CONINTUE_100;
import static org.glassfish.grizzly.http.util.HttpStatus.EXPECTATION_FAILED_417;
import static org.mule.runtime.http.api.HttpConstants.Method.HEAD;
import static org.mule.runtime.http.api.HttpConstants.Protocol.HTTP;
import static org.mule.runtime.http.api.HttpConstants.Protocol.HTTPS;
import static org.mule.runtime.http.api.HttpHeaders.Names.EXPECT;
import static org.mule.runtime.http.api.HttpHeaders.Values.CONTINUE;
import static org.mule.service.http.impl.service.server.grizzly.MuleSslFilter.SSL_SESSION_ATTRIBUTE_KEY;

import org.mule.runtime.http.api.domain.entity.EmptyHttpEntity;
import org.mule.runtime.http.api.domain.message.response.HttpResponseBuilder;
import org.mule.runtime.http.api.server.RequestHandler;
import org.mule.service.http.impl.service.server.RequestHandlerProvider;

import java.io.IOException;
import java.net.InetSocketAddress;

import javax.net.ssl.SSLSession;

import org.glassfish.grizzly.filterchain.BaseFilter;
import org.glassfish.grizzly.filterchain.FilterChainContext;
import org.glassfish.grizzly.filterchain.NextAction;
import org.glassfish.grizzly.http.HttpContent;
import org.glassfish.grizzly.http.HttpRequestPacket;
import org.glassfish.grizzly.http.HttpResponsePacket;

/**
 * Grizzly filter that dispatches the request to the right request handler
 */
public class GrizzlyRequestDispatcherFilter extends BaseFilter {

  private final RequestHandlerProvider requestHandlerProvider;

  GrizzlyRequestDispatcherFilter(final RequestHandlerProvider requestHandlerProvider) {
    this.requestHandlerProvider = requestHandlerProvider;
  }

  @Override
  public NextAction handleRead(final FilterChainContext ctx) throws IOException {
    final String scheme = (ctx.getAttributes().getAttribute(HTTPS.getScheme()) == null) ? HTTP.getScheme() : HTTPS.getScheme();
    final String ip = ((InetSocketAddress) ctx.getConnection().getLocalAddress()).getAddress().getHostAddress();
    final int port = ((InetSocketAddress) ctx.getConnection().getLocalAddress()).getPort();
    if (ctx.getMessage() instanceof HttpContent) {
      final HttpContent httpContent = ctx.getMessage();
      final HttpRequestPacket request = (HttpRequestPacket) httpContent.getHttpHeader();

      // Handle Expect Continue
      if (request.requiresAcknowledgement()) {
        final HttpResponsePacket.Builder responsePacketBuilder = HttpResponsePacket.builder(request);
        if (CONTINUE.equalsIgnoreCase(request.getHeader(EXPECT))) {
          responsePacketBuilder.status(CONINTUE_100.getStatusCode());
          HttpResponsePacket packet = responsePacketBuilder.build();
          packet.setAcknowledgement(true);
          ctx.write(packet);
          return ctx.getStopAction();
        } else {
          responsePacketBuilder.status(EXPECTATION_FAILED_417.getStatusCode());
          ctx.write(responsePacketBuilder.build());
          return ctx.getStopAction();
        }
      }

      final GrizzlyHttpRequestAdapter httpRequest = new GrizzlyHttpRequestAdapter(ctx, httpContent);
      DefaultHttpRequestContext requestContext = createRequestContext(ctx, scheme, httpRequest);
      final RequestHandler requestHandler = requestHandlerProvider.getRequestHandler(ip, port, httpRequest);
      requestHandler.handleRequest(requestContext, (httpResponse, responseStatusCallback) -> {
        try {
          if (httpRequest.getMethod().equals(HEAD.name())) {
            if (httpResponse.getEntity().isStreaming()) {
              httpResponse.getEntity().getContent().close();
            }
            httpResponse = new HttpResponseBuilder(httpResponse).entity(new EmptyHttpEntity()).build();
          }
          if (httpResponse.getEntity().isStreaming()) {
            new ResponseStreamingCompletionHandler(ctx, request, httpResponse, responseStatusCallback).start();
          } else {
            new ResponseCompletionHandler(ctx, request, httpResponse, responseStatusCallback).start();
          }
        } catch (Exception e) {
          responseStatusCallback.responseSendFailure(e);
        }
      });
      return ctx.getSuspendAction();
    } else {
      return ctx.getInvokeAction();
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
    return new DefaultHttpRequestContext(httpRequest, clientConnection, scheme);
  }

}
