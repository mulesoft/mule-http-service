/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.service.http.impl.service.server.grizzly;

import static org.mule.runtime.api.metadata.MediaType.MULTIPART_MIXED;
import static org.mule.runtime.http.api.HttpHeaders.Names.CONTENT_TYPE;
import org.mule.runtime.http.api.domain.entity.EmptyHttpEntity;
import org.mule.runtime.http.api.domain.entity.HttpEntity;
import org.mule.runtime.http.api.domain.entity.InputStreamHttpEntity;
import org.mule.runtime.http.api.domain.message.request.HttpRequest;
import org.mule.service.http.impl.service.domain.entity.multipart.StreamedMultipartHttpEntity;

import java.io.InputStream;
import java.net.InetSocketAddress;

import org.glassfish.grizzly.filterchain.FilterChainContext;
import org.glassfish.grizzly.http.HttpContent;
import org.glassfish.grizzly.http.HttpRequestPacket;
import org.glassfish.grizzly.utils.BufferInputStream;

public class GrizzlyHttpRequestAdapter extends GrizzlyHttpMessage implements HttpRequest {

  private static final String PROTOCOL = "http";

  private final InputStream requestContent;
  private HttpEntity body;

  public GrizzlyHttpRequestAdapter(FilterChainContext filterChainContext, HttpContent httpContent,
                                   InetSocketAddress localAddress) {
    this(filterChainContext, httpContent, (HttpRequestPacket) httpContent.getHttpHeader(), localAddress);
  }

  public GrizzlyHttpRequestAdapter(FilterChainContext filterChainContext,
                                   HttpContent httpContent,
                                   HttpRequestPacket requestPacket,
                                   InetSocketAddress localAddress) {
    super(requestPacket, null, localAddress);

    if (httpContent.isLast()) {
      requestContent = new BufferInputStream(httpContent.getContent());
    } else {
      requestContent = new BlockingTransferInputStream(requestPacket, filterChainContext);
    }
  }

  @Override
  protected String getBaseProtocol() {
    return PROTOCOL;
  }

  @Override
  public HttpEntity getEntity() {
    if (this.body == null) {
      final String contentTypeValue = getHeaderValue(CONTENT_TYPE);
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
}
