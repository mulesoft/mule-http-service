/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.service.http.impl.service.server.grizzly;

import static java.lang.Long.parseLong;
import static org.mule.runtime.core.api.util.StringUtils.isEmpty;
import static org.mule.runtime.http.api.HttpHeaders.Names.CONTENT_LENGTH;
import static org.mule.runtime.http.api.server.HttpServerProperties.PRESERVE_HEADER_CASE;
import static org.mule.runtime.http.api.utils.HttpEncoderDecoderUtils.decodeQueryString;
import static org.mule.runtime.http.api.utils.UriCache.getUriFromString;
import org.mule.runtime.api.util.MultiMap;
import org.mule.runtime.http.api.domain.CaseInsensitiveMultiMap;
import org.mule.runtime.http.api.domain.HttpProtocol;
import org.mule.runtime.http.api.domain.message.BaseHttpMessage;
import org.mule.runtime.http.api.domain.message.request.HttpRequest;

import java.net.InetSocketAddress;
import java.net.URI;
import java.util.Collection;

import org.glassfish.grizzly.http.HttpRequestPacket;
import org.glassfish.grizzly.http.Protocol;

public abstract class GrizzlyHttpMessage extends BaseHttpMessage implements HttpRequest {

  protected final InetSocketAddress localAddress;
  protected final boolean isTransferEncodingChunked;
  protected final long contentLength;
  protected final HttpRequestPacket requestPacket;

  protected String method;
  protected MultiMap<String, String> queryParams;
  protected HttpProtocol protocol;
  protected URI uri;
  protected String path;

  public GrizzlyHttpMessage(HttpRequestPacket requestPacket,
                            MultiMap<String, String> headers,
                            InetSocketAddress localAddress) {
    super(headers);
    this.requestPacket = requestPacket;
    this.localAddress = localAddress;
    isTransferEncodingChunked = requestPacket.isChunked();

    long contentLengthAsLong = -1L;
    String contentLengthAsString = requestPacket.getHeader(CONTENT_LENGTH);
    if (contentLengthAsString != null) {
      contentLengthAsLong = parseLong(contentLengthAsString);
    }
    this.contentLength = contentLengthAsLong;
  }

  @Override
  public MultiMap<String, String> getQueryParams() {
    if (queryParams == null) {
      queryParams = decodeQueryString(requestPacket.getQueryString());
    }
    return queryParams;
  }

  @Override
  public String getPath() {
    if (this.path == null) {
      this.path = requestPacket.getRequestURI();
    }
    return this.path;
  }

  @Override
  public String getMethod() {
    if (this.method == null) {
      this.method = requestPacket.getMethod().getMethodString();
    }
    return this.method;
  }

  @Override
  public HttpProtocol getProtocol() {
    if (this.protocol == null) {
      this.protocol = requestPacket.getProtocol() == Protocol.HTTP_1_0 ? HttpProtocol.HTTP_1_0 : HttpProtocol.HTTP_1_1;
    }
    return this.protocol;
  }

  @Override
  public Collection<String> getHeaderNames() {
    if (this.headers == null) {
      initializeHeaders();
    }
    return this.headers.keySet();
  }

  @Override
  public String getHeaderValue(String headerName) {
    if (this.headers == null) {
      initializeHeaders();
    }
    return this.headers.get(headerName);
  }

  @Override
  public Collection<String> getHeaderValues(String headerName) {
    if (this.headers == null) {
      initializeHeaders();
    }
    return this.headers.getAll(headerName);
  }

  @Override
  public MultiMap<String, String> getHeaders() {
    if (this.headers == null) {
      initializeHeaders();
    }
    return this.headers;
  }

  private void initializeHeaders() {
    this.headers = new CaseInsensitiveMultiMap(!PRESERVE_HEADER_CASE);
    for (String grizzlyHeaderName : requestPacket.getHeaders().names()) {
      final Iterable<String> headerValues = requestPacket.getHeaders().values(grizzlyHeaderName);
      for (String headerValue : headerValues) {
        this.headers.put(grizzlyHeaderName, headerValue);
      }
    }
    this.headers = this.headers.toImmutableMultiMap();
  }

  @Override
  public URI getUri() {
    if (this.uri == null) {
      String baseUri = getBaseProtocol() + "://" + localAddress.getHostName() + ":" + localAddress.getPort();
      this.uri = getUriFromString(baseUri + requestPacket.getRequestURI()
          + (isEmpty(requestPacket.getQueryString()) ? "" : "?" + requestPacket.getQueryString()));
    }
    return this.uri;
  }

  protected abstract String getBaseProtocol();
}
