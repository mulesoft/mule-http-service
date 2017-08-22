/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.service.http.impl.service.client;

import java.util.Collections;
import java.util.Map;
import org.apache.logging.log4j.ThreadContext;
import org.glassfish.grizzly.Buffer;
import org.glassfish.grizzly.Connection;
import org.glassfish.grizzly.http.HttpProbe;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Logger for plain HTTP request and response.
 */
public class HttpMessageLogger extends HttpProbe.Adapter {

  private static final Logger logger = LoggerFactory.getLogger(HttpMessageLogger.class);

  private final LoggerType loggerType;

  public enum LoggerType {
    LISTENER, REQUESTER
  }

  public HttpMessageLogger(final LoggerType loggerType) {
    this.loggerType = loggerType;
  }

  @Override
  public void onDataReceivedEvent(Connection connection, Buffer buffer) {
    logBuffer(buffer, getLogContext(connection));
  }

  @Override
  public void onDataSentEvent(Connection connection, Buffer buffer) {
    logBuffer(buffer, getLogContext(connection));
  }

  private static Map<String, String> getLogContext(Connection connection) {
    Map<String, String> logContext = (Map<String, String>) connection.getAttributes().getAttribute("logContext");
    return logContext == null ? Collections.<String, String>emptyMap() : logContext;
  }

  private void logBuffer(Buffer buffer, Map<String, String> logContext) {
    if (logger.isDebugEnabled()) {
      ThreadContext.putAll(logContext);
      logger.debug(loggerType.name() + "\n" + buffer.toStringContent());
      ThreadContext.removeAll(logContext.keySet());
    }
  }

}
