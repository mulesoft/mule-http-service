/*
 * Copyright 2023 Salesforce, Inc. All rights reserved.
 */
package org.mule.service.http.impl.service.server;

import static org.mule.runtime.http.api.HttpConstants.HttpStatus.BAD_REQUEST;

/**
 * Request handler for invalid requests (Bad Request).
 *
 * @since 1.2
 */
class BadRequestHandler extends ErrorRequestHandler {

  public static final String BAD_REQUEST_ENTITY_FORMAT = "Unable to parse request: %s";
  private static BadRequestHandler instance = new BadRequestHandler();

  private BadRequestHandler() {
    super(BAD_REQUEST.getStatusCode(), BAD_REQUEST.getReasonPhrase(), BAD_REQUEST_ENTITY_FORMAT);
  }

  public static BadRequestHandler getInstance() {
    return instance;
  }

}
