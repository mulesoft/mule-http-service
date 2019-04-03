/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.service.http.impl.service.server;

import org.mule.runtime.http.api.server.async.ResponseStatusCallback;

public class ErrorAwareResponseStatusCallback implements ResponseStatusCallback {

  @Override
  public void responseSendFailure(Throwable throwable) {

  }

  @Override
  public void responseSendSuccessfully() {

  }

  public void onErrorSendingResponse(Throwable throwable) {

  }
}
