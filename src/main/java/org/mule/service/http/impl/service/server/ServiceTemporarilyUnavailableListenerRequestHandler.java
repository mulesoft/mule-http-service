/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.service.http.impl.service.server;

import static org.mule.runtime.http.api.HttpConstants.HttpStatus.SERVICE_UNAVAILABLE;

/**
 * Request handle for request calls to paths with no listener configured.
 */
public class ServiceTemporarilyUnavailableListenerRequestHandler extends ErrorRequestHandler {

  public static final String SERVICE_NOT_AVAILABLE_FORMAT = "Service not available for endpoint: %s";
  private static ServiceTemporarilyUnavailableListenerRequestHandler instance =
      new ServiceTemporarilyUnavailableListenerRequestHandler();

  private ServiceTemporarilyUnavailableListenerRequestHandler() {
    super(SERVICE_UNAVAILABLE.getStatusCode(), SERVICE_UNAVAILABLE.getReasonPhrase(), SERVICE_NOT_AVAILABLE_FORMAT);
  }

  public static ServiceTemporarilyUnavailableListenerRequestHandler getInstance() {
    return instance;
  }

}
