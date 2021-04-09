/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.service.http.impl.service.util;

import static java.lang.Thread.currentThread;
import static org.mule.runtime.core.api.util.ClassUtils.setContextClassLoader;

import java.util.Map;

import org.slf4j.MDC;

/**
 * Auxiliary auto-closeable class to be used in a try-with-resources scope. Client would create an instance of ThreadContext
 * passing a class loader and a map to be set as mdc while the instance is open. When the instance goes out of scope, it's closed
 * and the previous class loader and mdc are restored.
 *
 * Usage example: <code>
 *   try (ThreadContext tc = new ThreadContext(theClassLoader, theMDC)) {
 *     // The code in this scope will use theClassLoader and theMDC.
 *   }
 *   // Out of the scope, the code will use the outer class loader and mdc.
 * </code>
 *
 * It's only intended to be used in a try-with-resources block, avoid using it in another fashion.
 */
public class ThreadContext implements AutoCloseable {

  private final Thread currentThread;

  private final ClassLoader innerClassLoader;
  private final Map<String, String> innerMDC;

  private final ClassLoader outerClassLoader;
  private final Map<String, String> outerMDC;

  public ThreadContext(ClassLoader classLoader, Map<String, String> mdc) {
    currentThread = currentThread();

    innerClassLoader = classLoader;
    innerMDC = mdc;

    outerClassLoader = currentThread.getContextClassLoader();
    outerMDC = MDC.getCopyOfContextMap();

    if (innerMDC != null) {
      MDC.setContextMap(innerMDC);
    }
    setContextClassLoader(currentThread, outerClassLoader, innerClassLoader);
  }

  @Override
  public void close() {
    try {
      setContextClassLoader(currentThread, innerClassLoader, outerClassLoader);
    } finally {
      if (innerMDC != null) {
        MDC.setContextMap(outerMDC);
      }
    }
  }
}
