/*
 * Copyright 2023 Salesforce, Inc. All rights reserved.
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.service.http.impl.service.client.sse;

import org.mule.runtime.http.api.client.HttpClient;
import org.mule.runtime.http.api.client.HttpRequestOptions;
import org.mule.runtime.http.api.domain.message.request.HttpRequest;
import org.mule.runtime.http.api.domain.message.response.HttpResponse;

import java.util.concurrent.CompletableFuture;

/**
 * Internal API of the HTTP client.
 */
public interface InternalClient {

  /**
   * Same as {@link HttpClient#sendAsync(HttpRequest, HttpRequestOptions)}, but it allows configuring a listener for incoming
   * data. Useful to execute non-blocking logic.
   * 
   * @param request      the request to be sent.
   * @param options      request options.
   * @param dataListener a callback to be executed when body data is received.
   * @return a future that will be completed once the response is received.
   */
  CompletableFuture<HttpResponse> doSendAsync(HttpRequest request, HttpRequestOptions options,
                                              ProgressiveBodyDataListener dataListener);
}
