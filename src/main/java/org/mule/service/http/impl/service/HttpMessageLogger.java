/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.service.http.impl.service;

import static org.slf4j.LoggerFactory.getLogger;
import org.mule.service.http.impl.service.util.ThreadContext;

import java.util.Map;

import org.glassfish.grizzly.Buffer;
import org.glassfish.grizzly.Connection;
import org.glassfish.grizzly.http.HttpProbe;
import org.slf4j.Logger;

/**
 * Logger for plain HTTP request and response.
 */
public class HttpMessageLogger extends HttpProbe.Adapter {

  private final Logger logger;
  private final LoggerType loggerType;
  private final ClassLoader classLoader;

  public static final String MDC_ATTRIBUTE_KEY = "mdc";

  public enum LoggerType {
    LISTENER, REQUESTER
  }

  HttpMessageLogger(final LoggerType loggerType, ClassLoader classLoader, Logger logger) {
    this.loggerType = loggerType;
    this.classLoader = classLoader;
    this.logger = logger;
  }

  public HttpMessageLogger(final LoggerType loggerType, String identifier, ClassLoader classLoader) {
    this(loggerType, classLoader, getLogger(HttpMessageLogger.class.getName() + "." + identifier));
  }

  @Override
  public void onDataReceivedEvent(Connection connection, Buffer buffer) {
    logBuffer(connection, buffer);
  }

  @Override
  public void onDataSentEvent(Connection connection, Buffer buffer) {
    logBuffer(connection, buffer);
  }

  private void logBuffer(Connection connection, Buffer buffer) {
    try (ThreadContext threadContext = createContext(connection)) {
      if (logger.isDebugEnabled()) {
        logger.debug("{}\n{}", loggerType.name(), buffer.toStringContent());
      }
    }
  }

  private ThreadContext createContext(Connection connection) {
    return new ThreadContext(classLoader, (Map<String, String>) connection.getAttributes().getAttribute(MDC_ATTRIBUTE_KEY));
  }
}
