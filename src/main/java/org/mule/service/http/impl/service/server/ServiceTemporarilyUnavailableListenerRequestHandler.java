/*
 * Copyright 2023 Salesforce, Inc. All rights reserved.
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
