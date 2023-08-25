/*
 * Copyright 2023 Salesforce, Inc. All rights reserved.
 */
package org.mule.service.http.impl.service.server;

import static org.mule.runtime.http.api.HttpConstants.HttpStatus.NOT_FOUND;

/**
 * Request handle for request calls to paths with no listener configured.
 */
public class NoListenerRequestHandler extends ErrorRequestHandler {

  public static final String RESOURCE_NOT_FOUND = "Resource not found.";

  public static final String NO_LISTENER_ENTITY_FORMAT = "No listener for endpoint: %s";

  private static NoListenerRequestHandler instance = new NoListenerRequestHandler();

  private NoListenerRequestHandler() {
    super(NOT_FOUND.getStatusCode(), NOT_FOUND.getReasonPhrase(), NO_LISTENER_ENTITY_FORMAT);
  }

  public static NoListenerRequestHandler getInstance() {
    return instance;
  }

}
