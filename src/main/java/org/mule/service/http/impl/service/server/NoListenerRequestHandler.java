/*
 * Copyright 2023 Salesforce, Inc. All rights reserved.
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
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
