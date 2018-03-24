/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.service.http.impl.service.server;

import static java.lang.String.format;
import static org.apache.commons.text.StringEscapeUtils.escapeHtml4;
import static org.mule.runtime.api.metadata.MediaType.TEXT;
import static org.mule.runtime.http.api.HttpHeaders.Names.CONTENT_TYPE;
import org.mule.runtime.http.api.domain.entity.InputStreamHttpEntity;
import org.mule.runtime.http.api.domain.message.response.HttpResponse;
import org.mule.runtime.http.api.domain.request.HttpRequestContext;
import org.mule.runtime.http.api.server.RequestHandler;
import org.mule.runtime.http.api.server.async.HttpResponseReadyCallback;
import org.mule.runtime.http.api.server.async.ResponseStatusCallback;

import java.io.ByteArrayInputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ErrorRequestHandler implements RequestHandler {

  private Logger logger = LoggerFactory.getLogger(getClass());

  private int statusCode;
  private String reasonPhrase;
  protected String entityFormat;

  public ErrorRequestHandler(int statusCode, String reasonPhrase, String entityFormat) {
    this.statusCode = statusCode;
    this.reasonPhrase = reasonPhrase;
    this.entityFormat = entityFormat;
  }

  @Override
  public void handleRequest(HttpRequestContext requestContext, HttpResponseReadyCallback responseCallback) {
    String resolvedEntity = getResolvedEntity(requestContext.getRequest().getPath());
    responseCallback.responseReady(HttpResponse.builder()
        .statusCode(statusCode).reasonPhrase(reasonPhrase)
        .entity(new InputStreamHttpEntity(new ByteArrayInputStream(resolvedEntity.getBytes())))
        .addHeader(CONTENT_TYPE, TEXT.toRfcString()).build(),
                                   new ResponseStatusCallback() {

                                     @Override
                                     public void responseSendFailure(Throwable exception) {
                                       logger.warn(format("Error while sending %s response %s", statusCode,
                                                          exception.getMessage()));
                                       if (logger.isDebugEnabled()) {
                                         logger.debug("exception thrown", exception);
                                       }
                                     }

                                     @Override
                                     public void responseSendSuccessfully() {}
                                   });
  }

  private String getResolvedEntity(String path) {
    return format(entityFormat, escapeHtml4(path));
  }

}
