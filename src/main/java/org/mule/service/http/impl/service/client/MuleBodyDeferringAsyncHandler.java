/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.service.http.impl.service.client;

import static org.mule.service.http.impl.service.client.RequestResourcesUtils.closeResources;

import java.io.IOException;
import java.io.OutputStream;

import org.mule.runtime.http.api.domain.message.request.HttpRequest;

import com.ning.http.client.BodyDeferringAsyncHandler;

public class MuleBodyDeferringAsyncHandler extends BodyDeferringAsyncHandler {

  private HttpRequest request;

  public MuleBodyDeferringAsyncHandler(HttpRequest request, OutputStream os) {
    super(os);
    this.request = request;
  }

  @Override
  protected void closeOut() throws IOException {
    closeResources(request);
    super.closeOut();
  }

}
