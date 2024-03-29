/*
 * Copyright 2023 Salesforce, Inc. All rights reserved.
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.service.http.impl.service;

import org.mule.runtime.api.exception.MuleException;
import org.mule.runtime.api.i18n.I18nMessage;
import org.mule.runtime.http.api.server.HttpServerFactory;

/**
 * Exception to be thrown when there is an error creating a {@link HttpServerFactory}
 */
public class ServerFactoryCreationException extends MuleException {

  public ServerFactoryCreationException(I18nMessage message) {
    super(message);
  }
}
