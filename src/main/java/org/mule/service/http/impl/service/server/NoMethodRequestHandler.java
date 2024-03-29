/*
 * Copyright 2023 Salesforce, Inc. All rights reserved.
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
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
