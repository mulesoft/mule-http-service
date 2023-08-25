/*
 * Copyright 2023 Salesforce, Inc. All rights reserved.
 */
package org.mule.service.http.impl.service.server;

import static org.mule.runtime.http.api.HttpConstants.HttpStatus.METHOD_NOT_ALLOWED;

public class NoMethodRequestHandler extends ErrorRequestHandler {

  public static final String METHOD_NOT_ALLOWED_FORMAT = "Method not allowed for endpoint: %s";
  private static NoMethodRequestHandler instance = new NoMethodRequestHandler();

  private NoMethodRequestHandler() {
    super(METHOD_NOT_ALLOWED.getStatusCode(), METHOD_NOT_ALLOWED.getReasonPhrase(), METHOD_NOT_ALLOWED_FORMAT);
  }

  public static NoMethodRequestHandler getInstance() {
    return instance;
  }

}
