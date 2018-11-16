/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.service.http.impl.service.server.grizzly;

import static java.lang.String.format;
import static java.lang.String.valueOf;
import static org.glassfish.grizzly.http.Protocol.HTTP_1_0;
import static org.mule.runtime.core.api.util.UUID.getUUID;
import static org.mule.runtime.http.api.HttpHeaders.Names.CONNECTION;
import static org.mule.runtime.http.api.HttpHeaders.Names.CONTENT_LENGTH;
import static org.mule.runtime.http.api.HttpHeaders.Names.CONTENT_TYPE;
import static org.mule.runtime.http.api.HttpHeaders.Names.TRANSFER_ENCODING;
import static org.mule.runtime.http.api.HttpHeaders.Values.BOUNDARY;
import static org.mule.runtime.http.api.HttpHeaders.Values.MULTIPART_FORM_DATA;
import static org.slf4j.LoggerFactory.getLogger;

import org.mule.runtime.http.api.domain.message.response.HttpResponse;

import org.glassfish.grizzly.EmptyCompletionHandler;
import org.glassfish.grizzly.WriteResult;
import org.glassfish.grizzly.http.HttpRequestPacket;
import org.glassfish.grizzly.http.HttpResponsePacket;
import org.glassfish.grizzly.http.Protocol;
import org.slf4j.Logger;

import java.util.Optional;

public abstract class BaseResponseCompletionHandler extends EmptyCompletionHandler<WriteResult> {

  private static final Logger LOGGER = getLogger(BaseResponseCompletionHandler.class);
  private static final String MULTIPART_CONTENT_TYPE_FORMAT = "%s; %s=\"%s\"";

  protected boolean hasContentLength = false;

  protected HttpResponsePacket buildHttpResponsePacket(HttpRequestPacket sourceRequest, HttpResponse httpResponse) {
    final HttpResponsePacket.Builder responsePacketBuilder = HttpResponsePacket.builder(sourceRequest)
        .status(httpResponse.getStatusCode()).reasonPhrase(httpResponse.getReasonPhrase());

    String contentType = null;
    boolean hasTransferEncoding = false;
    boolean hasConnection = false;

    for (String headerName : httpResponse.getHeaderNames()) {
      // This is a workaround for https://github.com/javaee/grizzly/issues/1994
      boolean specialHeader = false;

      if (contentType == null && headerName.equalsIgnoreCase(CONTENT_TYPE)) {
        contentType = httpResponse.getHeaderValue(headerName);
        specialHeader = true;
        responsePacketBuilder.header(CONTENT_TYPE, httpResponse.getHeaderValue(headerName));
      }
      if (!hasTransferEncoding && headerName.equalsIgnoreCase(TRANSFER_ENCODING)) {
        hasTransferEncoding = true;
        specialHeader = true;
        responsePacketBuilder.header(TRANSFER_ENCODING, httpResponse.getHeaderValue(headerName));
      }
      if (!hasConnection && headerName.equalsIgnoreCase(CONNECTION)) {
        hasConnection = true;
        specialHeader = true;
        responsePacketBuilder.header(CONNECTION, httpResponse.getHeaderValue(headerName));
      }
      if (!hasContentLength && headerName.equalsIgnoreCase(CONTENT_LENGTH)) {
        hasContentLength = true;
        specialHeader = true;
        responsePacketBuilder.header(CONTENT_LENGTH, httpResponse.getHeaderValue(headerName));
      }

      if (!specialHeader) {
        for (String value : httpResponse.getHeaderValues(headerName)) {
          responsePacketBuilder.header(headerName, value);
        }
      }
    }
    if (httpResponse.getEntity().isComposed()) {
      if (contentType == null) {
        responsePacketBuilder.header(CONTENT_TYPE,
                                     format(MULTIPART_CONTENT_TYPE_FORMAT, MULTIPART_FORM_DATA, BOUNDARY, getUUID()));
      } else if (!contentType.contains(BOUNDARY)) {
        responsePacketBuilder.removeHeader(CONTENT_TYPE);
        responsePacketBuilder.header(CONTENT_TYPE, format(MULTIPART_CONTENT_TYPE_FORMAT, contentType, BOUNDARY, getUUID()));
      }
    }

    // If there's no transfer type specified, check the entity length to prioritize content length transfer (unless it's 1.0)
    Optional<Long> length = httpResponse.getEntity().getLength();
    Protocol protocol = sourceRequest.getProtocol();
    if (!hasTransferEncoding && !hasContentLength && length.isPresent() && !protocol.equals(HTTP_1_0)) {
      responsePacketBuilder.header(CONTENT_LENGTH, valueOf(length.get()));
    }

    HttpResponsePacket httpResponsePacket = responsePacketBuilder.build();
    httpResponsePacket.setProtocol(protocol);
    if (hasTransferEncoding) {
      httpResponsePacket.setChunked(true);
    }

    if (hasConnection) {
      httpResponsePacket.getProcessingState().setKeepAlive(false);
    }
    return httpResponsePacket;
  }

  @Override
  public void cancelled() {
    LOGGER.warn("HTTP response sending task was cancelled");
  }

  @Override
  public void failed(Throwable throwable) {
    if (LOGGER.isWarnEnabled()) {
      LOGGER.warn(format("HTTP response sending task failed with error: %s", throwable.getMessage()));
    }
  }

}
