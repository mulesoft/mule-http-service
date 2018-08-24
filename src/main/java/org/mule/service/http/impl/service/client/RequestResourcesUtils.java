/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.service.http.impl.service.client;

import java.io.IOException;

import org.mule.runtime.http.api.domain.message.request.HttpRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utils to manage request resources
 * 
 * @since 2.1.0
 *
 */
public class RequestResourcesUtils {

  private static final Logger logger = LoggerFactory.getLogger(RequestResourcesUtils.class);

  public static void closeResources(HttpRequest request) {
    if (request != null && request.getEntity() != null && request.getEntity().getContent() != null)
      try {
        request.getEntity().getContent().close();
      } catch (IOException e) {
        logger.warn("Error on closing http client stream.");
      }
  }
}
