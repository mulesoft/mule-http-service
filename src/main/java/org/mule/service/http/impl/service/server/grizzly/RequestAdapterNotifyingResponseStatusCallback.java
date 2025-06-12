/*
 * Copyright 2023 Salesforce, Inc. All rights reserved.
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.service.http.impl.service.server.grizzly;

import org.mule.runtime.http.api.server.async.ResponseStatusCallback;

/**
 * Wrapper for a {@link ResponseStatusCallback} which will notify the given {@link GrizzlyHttpRequestAdapter} about the response
 * having been sent (either successfully or unsuccessfully).
 */
public class RequestAdapterNotifyingResponseStatusCallback implements ResponseStatusCallback {

  private final GrizzlyHttpRequestAdapter httpRequestAdapter;
  private final ResponseStatusCallback delegate;

  public RequestAdapterNotifyingResponseStatusCallback(GrizzlyHttpRequestAdapter httpRequestAdapter,
                                                       ResponseStatusCallback delegate) {
    this.httpRequestAdapter = httpRequestAdapter;
    this.delegate = delegate;
  }

  @Override
  public void responseSendFailure(Throwable throwable) {
    httpRequestAdapter.responseSent();
    delegate.responseSendFailure(throwable);
  }

  @Override
  public void responseSendSuccessfully() {
    httpRequestAdapter.responseSent();
    delegate.responseSendSuccessfully();
  }

  @Override
  public void onErrorSendingResponse(Throwable throwable) {
    httpRequestAdapter.responseSent();
    delegate.onErrorSendingResponse(throwable);
  }
}
