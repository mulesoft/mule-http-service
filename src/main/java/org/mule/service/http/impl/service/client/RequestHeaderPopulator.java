/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.service.http.impl.service.client;

import static org.mule.runtime.http.api.HttpHeaders.Names.CONNECTION;
import static org.mule.runtime.http.api.HttpHeaders.Names.CONTENT_LENGTH;
import static org.mule.runtime.http.api.HttpHeaders.Names.COOKIE;
import static org.mule.runtime.http.api.HttpHeaders.Names.TRANSFER_ENCODING;
import static org.mule.runtime.http.api.HttpHeaders.Values.CLOSE;
import static org.mule.runtime.http.api.server.HttpServerProperties.PRESERVE_HEADER_CASE;

import static java.lang.String.valueOf;

import static com.ning.http.client.cookie.CookieDecoder.decode;

import org.mule.runtime.http.api.domain.message.request.HttpRequest;

import java.util.Collection;

import com.ning.http.client.RequestBuilder;
import com.ning.http.client.cookie.Cookie;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RequestHeaderPopulator {

  private static final Logger LOGGER = LoggerFactory.getLogger(RequestHeaderPopulator.class);

  private final boolean usePersistentConnections;

  public RequestHeaderPopulator(boolean usePersistentConnections) {
    this.usePersistentConnections = usePersistentConnections;
  }

  private static final String HEADER_CONNECTION = CONNECTION.toLowerCase();
  private static final String HEADER_CONTENT_LENGTH = CONTENT_LENGTH.toLowerCase();
  private static final String HEADER_TRANSFER_ENCODING = TRANSFER_ENCODING.toLowerCase();
  private static final String HEADER_COOKIE = COOKIE.toLowerCase();
  private static final String COOKIE_SEPARATOR = ";";

  public void populateHeaders(HttpRequest request, RequestBuilder builder) {
    boolean hasTransferEncoding = false;
    boolean hasContentLength = false;
    boolean hasConnection = false;

    for (String headerName : request.getHeaderNames()) {
      // This is a workaround for https://github.com/javaee/grizzly/issues/1994
      boolean specialHeader = false;

      if (!hasTransferEncoding && headerName.equalsIgnoreCase(HEADER_TRANSFER_ENCODING)) {
        hasTransferEncoding = true;
        specialHeader = true;
        builder.addHeader(PRESERVE_HEADER_CASE ? TRANSFER_ENCODING : HEADER_TRANSFER_ENCODING,
                          request.getHeaderValue(headerName));
      }
      if (!hasContentLength && headerName.equalsIgnoreCase(HEADER_CONTENT_LENGTH)) {
        hasContentLength = true;
        specialHeader = true;
        builder.addHeader(PRESERVE_HEADER_CASE ? CONTENT_LENGTH : HEADER_CONTENT_LENGTH, request.getHeaderValue(headerName));
      }
      if (!hasContentLength && headerName.equalsIgnoreCase(HEADER_CONNECTION)) {
        hasConnection = true;
        specialHeader = true;
        builder.addHeader(PRESERVE_HEADER_CASE ? CONNECTION : HEADER_CONNECTION, request.getHeaderValue(headerName));
      }
      if (headerName.equalsIgnoreCase(HEADER_COOKIE)) {
        specialHeader = true;
        parseCookieHeaderAndAddCookies(builder, request.getHeaderValues(headerName));
      }

      if (!specialHeader) {
        for (String headerValue : request.getHeaderValues(headerName)) {
          builder.addHeader(headerName, headerValue);
        }
      }
    }

    // If there's no transfer type specified, check the entity length to prioritize content length transfer
    if (!hasTransferEncoding && !hasContentLength && request.getEntity().getBytesLength().isPresent()) {
      builder.addHeader(PRESERVE_HEADER_CASE ? CONTENT_LENGTH : HEADER_CONTENT_LENGTH,
                        valueOf(request.getEntity().getBytesLength().getAsLong()));
    }

    // If persistent connections are disabled, the "Connection: close" header must be explicitly added. AHC will
    // add "Connection: keep-alive" otherwise. (https://github.com/AsyncHttpClient/async-http-client/issues/885)

    if (!usePersistentConnections) {
      if (hasConnection && LOGGER.isDebugEnabled() && !CLOSE.equals(request.getHeaderValue(HEADER_CONNECTION))) {
        LOGGER.debug("Persistent connections are disabled in the HTTP requester configuration, but the request already "
            + "contains a Connection header with value {}. This header will be ignored, and a Connection: close header "
            + "will be sent instead.", request.getHeaderValue(HEADER_CONNECTION));
      }
      builder.setHeader(PRESERVE_HEADER_CASE ? CONNECTION : HEADER_CONNECTION, CLOSE);
    }
  }

  private void parseCookieHeaderAndAddCookies(RequestBuilder builder, Collection<String> headerValues) {
    for (String cookieHeader : headerValues) {
      for (String eachCookie : cookieHeader.split(COOKIE_SEPARATOR)) {
        Cookie decodedCookiePair = decode(eachCookie.trim());
        if (decodedCookiePair == null) {
          LOGGER.debug("Couldn't decode '' as a cookie-pair. See RFC-6265, section 4.2.1 (Cookie header syntax)");
        } else {
          builder.addOrReplaceCookie(decodedCookiePair);
        }
      }
    }
  }
}
