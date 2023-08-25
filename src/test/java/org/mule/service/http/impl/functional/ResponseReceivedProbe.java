/*
 * Copyright 2023 Salesforce, Inc. All rights reserved.
 */
package org.mule.service.http.impl.functional;

import org.mule.runtime.api.util.Reference;
import org.mule.runtime.http.api.domain.message.response.HttpResponse;
import org.mule.tck.probe.Probe;

public class ResponseReceivedProbe implements Probe {

  private Reference<HttpResponse> responseReference;

  public ResponseReceivedProbe(Reference<HttpResponse> responseReference) {
    this.responseReference = responseReference;
  }

  @Override
  public boolean isSatisfied() {
    return responseReference.get() != null;
  }

  @Override
  public String describeFailure() {
    return "Response was not received.";
  }
}
