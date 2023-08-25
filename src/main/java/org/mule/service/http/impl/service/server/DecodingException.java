/*
 * Copyright 2023 Salesforce, Inc. All rights reserved.
 */
package org.mule.service.http.impl.service.server;

import org.mule.runtime.api.exception.MuleException;

import static org.mule.runtime.api.i18n.I18nMessageFactory.createStaticMessage;

/**
 * {@code DecodingException} Is an exception thrown when there is attempt to decode a malformed or invalid text, url or url
 * parameter.
 *
 * @since 1.2
 */
public class DecodingException extends MuleException {

  public DecodingException(String message, Throwable cause) {
    super(createStaticMessage(message), cause);
  }

}
