/*
 * Copyright 2023 Salesforce, Inc. All rights reserved.
 */
package org.mule.service.http.impl.service.client;

import static org.mule.runtime.api.util.MuleSystemProperties.SYSTEM_PROPERTY_PREFIX;
import static org.mule.runtime.http.api.HttpHeaders.Names.CONNECTION;
import static org.mule.runtime.http.api.HttpHeaders.Names.CONTENT_LENGTH;
import static org.mule.runtime.http.api.HttpHeaders.Names.COOKIE;
import static org.mule.runtime.http.api.HttpHeaders.Names.TRANSFER_ENCODING;
import static org.mule.runtime.http.api.HttpHeaders.Values.CLOSE;
import static org.mule.runtime.http.api.server.HttpServerProperties.PRESERVE_HEADER_CASE;

import static java.lang.Boolean.getBoolean;
import static java.lang.String.valueOf;

import static com.ning.http.client.cookie.CookieDecoder.decode;

import org.mule.runtime.http.api.domain.message.request.HttpRequest;

import java.util.Collection;

import com.ning.http.client.RequestBuilder;
import com.ning.http.client.cookie.Cookie;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utility class used to pass the headers present in a {@link HttpRequest} (from Mule HTTP API) to a {@link RequestBuilder} (from
 * Grizzly AHC).
 */
public class RequestHeaderPopulator {

  private static final String DISABLE_COOKIE_SPECIAL_HANDLING_PROPERTY =
      SYSTEM_PROPERTY_PREFIX + "http.cookie.special.handling.disable";
  private static final boolean DISABLE_COOKIE_SPECIAL_HANDLING = getBoolean(DISABLE_COOKIE_SPECIAL_HANDLING_PROPERTY);

  private static final Logger LOGGER = LoggerFactory.getLogger(RequestHeaderPopulator.class);
  private static final String HEADER_CONNECTION = CONNECTION.toLowerCase();
  private static final String HEADER_CONTENT_LENGTH = CONTENT_LENGTH.toLowerCase();
  private static final String HEADER_TRANSFER_ENCODING = TRANSFER_ENCODING.toLowerCase();
  private static final String HEADER_COOKIE = COOKIE.toLowerCase();
  private static final String COOKIE_SEPARATOR = ";";

  private final boolean usePersistentConnections;

  public RequestHeaderPopulator(boolean usePersistentConnections) {
    this.usePersistentConnections = usePersistentConnections;
  }

  /**
   * Populates the headers in a {@link RequestBuilder} with the ones configured in a {@link HttpRequest}.
   * 
   * @param request the {@link HttpRequest} from Mule HTTP API.
   * @param builder the {@link RequestBuilder} from Grizzly AHC.
   */
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
      if (mustTreatCookieAsASpecialHeader() && headerName.equalsIgnoreCase(HEADER_COOKIE)) {
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

  private boolean mustTreatCookieAsASpecialHeader() {
    return !DISABLE_COOKIE_SPECIAL_HANDLING;
  }

  private void parseCookieHeaderAndAddCookies(RequestBuilder builder, Collection<String> headerValues) {
    try {
      if (headerValues == null) {
        LOGGER.warn("A null value was retrieved as the collection of cookie headers");
        return;
      }

      for (String cookieHeader : headerValues) {
        if (cookieHeader == null) {
          LOGGER.warn("Detected a cookie header with a null value");
          continue;
        }

        for (String eachCookie : cookieHeader.split(COOKIE_SEPARATOR)) {
          // String#split() never returns null.
          eachCookie = eachCookie.trim();
          Cookie decodedCookiePair = decode(eachCookie.trim());
          if (decodedCookiePair == null) {
            LOGGER.warn("Couldn't decode '{}' as a cookie-pair. See RFC-6265, section 4.2.1 (Cookie header syntax)", eachCookie);
          } else {
            builder.addOrReplaceCookie(decodedCookiePair);
          }
        }
      }
    } catch (NullPointerException npe) {
      LOGGER.error("This should never happen, but it was added because of repeated problems with NPEs in this code", npe);
    }
  }
}
