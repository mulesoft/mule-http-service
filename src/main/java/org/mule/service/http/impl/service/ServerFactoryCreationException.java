/*
 * Copyright 2023 Salesforce, Inc. All rights reserved.
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
