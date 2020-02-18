/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.service.http.impl.service;

import static java.lang.Thread.currentThread;
import static org.mule.runtime.core.api.util.ClassUtils.setContextClassLoader;

import org.glassfish.grizzly.Buffer;
import org.glassfish.grizzly.Connection;
import org.glassfish.grizzly.http.HttpProbe;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Logger for plain HTTP request and response.
 */
public class HttpMessageLogger extends HttpProbe.Adapter {

  private final Logger logger;
  private final LoggerType loggerType;
  private final ClassLoader classLoader;

  public enum LoggerType {
    LISTENER, REQUESTER
  }

  public HttpMessageLogger(final LoggerType loggerType, String identifier, ClassLoader classLoader) {
    this.loggerType = loggerType;
    this.classLoader = classLoader;
    this.logger = LoggerFactory.getLogger(HttpMessageLogger.class.getName() + "." + identifier);
  }

  @Override
  public void onDataReceivedEvent(Connection connection, Buffer buffer) {
    logBuffer(buffer);
  }

  @Override
  public void onDataSentEvent(Connection connection, Buffer buffer) {
    logBuffer(buffer);
  }

  private void logBuffer(Buffer buffer) {
    Thread currentThread = currentThread();
    ClassLoader originalClassLoader = currentThread.getContextClassLoader();
    setContextClassLoader(currentThread, originalClassLoader, classLoader);
    try {
      if (logger.isDebugEnabled()) {
        logger.debug(loggerType.name() + "\n" + buffer.toStringContent());
      }
    } finally {
      setContextClassLoader(currentThread, classLoader, originalClassLoader);
    }
  }
}
