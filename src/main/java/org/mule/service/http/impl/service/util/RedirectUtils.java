/*
 * Copyright 2023 Salesforce, Inc. All rights reserved.
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.service.http.impl.service.util;

import static com.ning.http.client.cookie.CookieDecoder.decode;
import static com.ning.http.client.uri.Uri.create;
import static org.glassfish.grizzly.http.util.Header.Authorization;
import static org.glassfish.grizzly.http.util.Header.ContentLength;
import static org.glassfish.grizzly.http.util.Header.ContentType;
import static org.glassfish.grizzly.http.util.Header.Host;
import static org.glassfish.grizzly.http.util.Header.ProxyAuthorization;
import static org.glassfish.grizzly.http.util.Header.SetCookie;

import static org.mule.runtime.http.api.HttpHeaders.Names.LOCATION;
import static org.mule.runtime.http.api.client.auth.HttpAuthenticationType.NTLM;
import static org.mule.runtime.http.api.domain.message.request.HttpRequest.builder;
import static org.mule.runtime.http.api.HttpConstants.Method.GET;

import org.mule.runtime.api.util.MultiMap;

import org.mule.runtime.http.api.client.HttpRequestOptions;
import org.mule.runtime.http.api.domain.entity.EmptyHttpEntity;
import org.mule.runtime.http.api.domain.entity.HttpEntity;
import org.mule.runtime.http.api.domain.message.request.HttpRequest;
import org.mule.runtime.http.api.domain.message.response.HttpResponse;

import com.ning.http.client.RequestBuilder;
import com.ning.http.client.uri.Uri;

/**
 * Redirect utilities.
 */
public class RedirectUtils {

  private final boolean isStrict302Handling;
  private final boolean preserveHeaderCase;

  public RedirectUtils(boolean isStrict302Handling, boolean preserveHeaderCase) {
    this.isStrict302Handling = isStrict302Handling;
    this.preserveHeaderCase = preserveHeaderCase;
  }

  /**
   * @param response HttpResponse
   * @param options  HttpRequestOptions
   * @return a boolean indicating if the response contains a redirect status and the LOCATION header.
   */
  public boolean shouldFollowRedirect(HttpResponse response, HttpRequestOptions options, boolean enableMuleRedirect) {
    return enableMuleRedirect && isRedirected(response.getStatusCode())
        && response.getHeaders().containsKey(LOCATION) && options.isFollowsRedirect();
  }

  /**
   * @param statusCode
   * @return if the status code is a redirect one.
   */
  // Copy from ResponseBase
  private boolean isRedirected(int statusCode) {
    switch (statusCode) {
      case 301:
      case 302:
      case 303:
      case 307:
      case 308:
        return true;
      default:
        return false;
    }
  }

  /**
   * Copy from RedirectHandler#sendAsGet.
   *
   * @param statusCode
   * @return false if the status code is 301, 307 or 308, or true if it is 302 or 303.
   */
  private boolean mustBeSendAsGet(int statusCode) {
    return !(statusCode < 302 || statusCode > 303) && !(statusCode == 302 && isStrict302Handling);
  }

  /**
   * Create a new request with the params of the original and the new URI from the LOCATION header. This method is copy from
   * AhcEventFilter#RedirectHandler.newRequest. The set-cookie header is handle in GrizzlyHttpClient.createGrizzlyRedirectRequest
   *
   * @param response HttpResponse
   * @param request  HttpRequest
   * @return an HttpRequest request.
   */
  public HttpRequest createRedirectRequest(HttpResponse response, HttpRequest request, HttpRequestOptions options) {
    Uri path = create(create(request.getUri().toString()), response.getHeaders().get(LOCATION));

    MultiMap<String, String> headers = new MultiMap<>(request.getHeaders());
    removeHeader(headers, Host.toString());
    removeHeader(headers, ContentLength.toString());
    String redirectMethod;
    HttpEntity entity = request.getEntity();

    if (mustBeSendAsGet(response.getStatusCode())) {
      redirectMethod = GET.name();
      removeHeader(headers, ContentType.toString());
      if (!options.shouldSendBodyAlways()) {
        entity = new EmptyHttpEntity();
      }
    } else {
      redirectMethod = request.getMethod();
    }

    options.getAuthentication().ifPresent(httpAuthentication -> {
      if (httpAuthentication.getType().equals(NTLM)) {
        removeHeader(headers, Authorization.toString());
        removeHeader(headers, ProxyAuthorization.toString());
      }
    });

    return builder(preserveHeaderCase).uri(path.toUrl()).method(redirectMethod)
        .protocol(request.getProtocol()).headers(headers).entity(entity).build();
  }

  private static void removeHeader(MultiMap<String, String> headers, String header) {
    headers.keySet().removeIf(key -> key.equalsIgnoreCase(header));
  }

  /**
   * Sets the values present in the response set-cookies header to the next request builder cookies.
   *
   * @param requestBuilder partial {@link RequestBuilder} without cookies set by the server response.
   * @param response       the {@link HttpResponse} from the server.
   */
  public void handleResponseCookies(RequestBuilder requestBuilder, HttpResponse response) {
    for (String cookieStr : response.getHeaderValues(SetCookie.toString())) {
      requestBuilder.addOrReplaceCookie(decode(cookieStr));
    }
  }
}
