/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.service.http.impl.service.server.grizzly;

import static java.lang.Long.parseLong;
import static org.mule.runtime.api.metadata.MediaType.MULTIPART_MIXED;
import static org.mule.runtime.core.api.util.StringUtils.isEmpty;
import static org.mule.runtime.http.api.HttpHeaders.Names.CONTENT_LENGTH;
import static org.mule.runtime.http.api.HttpHeaders.Names.CONTENT_TYPE;
import static org.mule.runtime.http.api.utils.HttpEncoderDecoderUtils.decodeQueryString;

import com.ning.http.client.uri.Uri;
import org.mule.runtime.api.util.MultiMap;
import org.mule.runtime.http.api.domain.HttpProtocol;
import org.mule.runtime.http.api.domain.entity.EmptyHttpEntity;
import org.mule.runtime.http.api.domain.entity.HttpEntity;
import org.mule.runtime.http.api.domain.entity.InputStreamHttpEntity;
import org.mule.runtime.http.api.domain.message.BaseHttpMessage;
import org.mule.runtime.http.api.domain.message.request.HttpRequest;
import org.mule.service.http.impl.service.domain.entity.multipart.StreamedMultipartHttpEntity;

import java.io.InputStream;
import java.net.URI;
import java.util.Collection;

import org.glassfish.grizzly.filterchain.FilterChainContext;
import org.glassfish.grizzly.http.HttpContent;
import org.glassfish.grizzly.http.HttpRequestPacket;
import org.glassfish.grizzly.http.Protocol;
import org.glassfish.grizzly.utils.BufferInputStream;

public class GrizzlyHttpRequestAdapter extends BaseHttpMessage implements HttpRequest {

  private final HttpRequestPacket requestPacket;
  private final InputStream requestContent;
  private final long contentLength;
  private final boolean isTransferEncodingChunked;
  private HttpProtocol protocol;
  private URI uri;
  private String path;
  private String method;
  private HttpEntity body;
  private MultiMap<String, String> headers;
  private MultiMap<String, String> queryParams;

  public GrizzlyHttpRequestAdapter(FilterChainContext filterChainContext, HttpContent httpContent) {
    this.requestPacket = (HttpRequestPacket) httpContent.getHttpHeader();
    isTransferEncodingChunked = requestPacket.isChunked();
    long contentLengthAsLong = -1L;
    String contentLengthAsString = requestPacket.getHeader(CONTENT_LENGTH);
    if (contentLengthAsString != null) {
      contentLengthAsLong = parseLong(contentLengthAsString);
    }
    this.contentLength = contentLengthAsLong;
    if (httpContent.isLast()) {
      requestContent = new BufferInputStream(httpContent.getContent());
    } else {
      requestContent = new BlockingTransferInputStream(requestPacket, filterChainContext);
    }
  }

  @Override
  public HttpProtocol getProtocol() {
    if (this.protocol == null) {
      this.protocol = requestPacket.getProtocol() == Protocol.HTTP_1_0 ? HttpProtocol.HTTP_1_0 : HttpProtocol.HTTP_1_1;
    }
    return this.protocol;
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

  private void initializeHeaders() {
    this.headers = new MultiMap<>();
    for (String grizzlyHeaderName : requestPacket.getHeaders().names()) {
      final Iterable<String> headerValues = requestPacket.getHeaders().values(grizzlyHeaderName);
      for (String headerValue : headerValues) {
        this.headers.put(grizzlyHeaderName, headerValue);
      }
    }
    this.headers = this.headers.toImmutableMultiMap();
  }

  @Override
  public HttpEntity getEntity() {
    if (this.body == null) {
      final String contentTypeValue = getHeaderValueIgnoreCase(CONTENT_TYPE);
      if (contentTypeValue != null && contentTypeValue.contains(MULTIPART_MIXED.getPrimaryType())) {
        if (contentLength >= 0) {
          this.body = new StreamedMultipartHttpEntity(requestContent, contentTypeValue, contentLength);
        } else {
          this.body = new StreamedMultipartHttpEntity(requestContent, contentTypeValue);
        }
      } else {
        if (contentLength > 0) {
          this.body = new InputStreamHttpEntity(requestContent, contentLength);
        } else if (contentLength == 0) {
          this.body = new EmptyHttpEntity();
        } else {
          this.body = new InputStreamHttpEntity(requestContent);
        }
      }
    }
    return this.body;
  }

  @Override
  public MultiMap<String, String> getQueryParams() {
    if (queryParams == null) {
      queryParams = decodeQueryString(requestPacket.getQueryString());
    }
    return queryParams;
  }

  @Override
  public URI getUri() {
    if (this.uri == null) {
      this.uri = URI.create(
                            requestPacket.getRequestURI()
                                + (isEmpty(requestPacket.getQueryString()) ? "" : "?" + requestPacket.getQueryString()));
    }
    return this.uri;
  }

}
