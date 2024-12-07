/*
 * Copyright 2023 Salesforce, Inc. All rights reserved.
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.service.http.impl.service.server.grizzly;

import jakarta.mail.internet.MimeMultipart;

public class HttpMimeMultipart extends MimeMultipart {

  public HttpMimeMultipart(String contentType, String subtype) {
    super(subtype);
    this.contentType = contentType;
  }
}
