/*
 * Copyright 2023 Salesforce, Inc. All rights reserved.
 */
package org.mule.service.http.impl.service.server.grizzly;

import javax.mail.internet.MimeMultipart;

public class HttpMimeMultipart extends MimeMultipart {

  public HttpMimeMultipart(String contentType, String subtype) {
    super(subtype);
    this.contentType = contentType;
  }
}
