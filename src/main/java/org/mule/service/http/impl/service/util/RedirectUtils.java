/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.service.http.impl.service.util;

import static com.ning.http.client.uri.Uri.create;
import static org.mule.runtime.http.api.HttpHeaders.Names.LOCATION;
import static org.mule.runtime.http.api.domain.message.request.HttpRequest.builder;

import org.mule.runtime.http.api.HttpConstants;
import org.mule.runtime.http.api.client.HttpRequestOptions;
import org.mule.runtime.http.api.domain.message.request.HttpRequest;
import org.mule.runtime.http.api.domain.message.response.HttpResponse;

import com.ning.http.client.uri.Uri;

/**
 * Redirect utilities.
 */
public class RedirectUtils {

  /**
   * @param response HttpResponse
   * @param options  HttpRequestOptions
   * @return a boolean indicating if the response contains a redirect status and the LOCATION header.
   */
  public static boolean shouldFollowRedirect(HttpResponse response, HttpRequestOptions options) {
    return isRedirected(response.getStatusCode()) && response.getHeaders().containsKey(LOCATION) && options.isFollowsRedirect();
  }

  /**
   * @param statusCode
   * @return if the status code is a redirect one.
   */
  // Copy from ResponseBase
  public static boolean isRedirected(int statusCode) {
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
   * @param requestMethod
   * @param responseStatusCode
   * @return the original request method if the status code is 301, 307 or 308, or GET if it is 302 or 303.
   */
  public static String getMethodForStatusCode(String requestMethod, int responseStatusCode) {
    switch (responseStatusCode) {
      case 301:
      case 307:
      case 308:
        return requestMethod;
      case 302:
      case 303:
        return HttpConstants.Method.GET.name();
      default:
        throw new IllegalArgumentException("Invalid status code");
    }
  }

  /**
   * Create a new request with the params of the original and the new URI from the LOCATION header.
   * 
   * @param response HttpResponse
   * @param request  HttpRequest
   * @return an HttpRequest request.
   */
  public static HttpRequest createRedirectRequest(HttpResponse response, HttpRequest request) {
    Uri path = create(create(request.getUri().toString()), response.getHeaders().get(LOCATION)).withNewQuery(null);
    return builder().uri(path.toUrl()).method(getMethodForStatusCode(request.getMethod(), response.getStatusCode()))
        .protocol(request.getProtocol()).headers(request.getHeaders())
        .queryParams(request.getQueryParams()).entity(request.getEntity())
        .build();
  }
}
