/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.service.http.impl.service.client.async;

import static java.lang.Thread.currentThread;
import static org.mule.runtime.api.util.Preconditions.checkState;

import com.ning.http.client.AsyncHandler;
import com.ning.http.client.HttpResponseBodyPart;
import com.ning.http.client.HttpResponseHeaders;
import com.ning.http.client.HttpResponseStatus;

/**
 * Wrapper class used to preserve the creation classloader on async handling methods.
 *
 * @param <T> See {@link com.ning.http.client.AsyncHandler}
 */
public class PreservingClassLoaderAsyncHandler<T> implements AsyncHandler<T> {

  private AsyncHandler<T> delegate;
  private final ClassLoader contextClassLoader;

  public PreservingClassLoaderAsyncHandler(AsyncHandler<T> delegate) {
    checkState(delegate != null, "Delegate cannot be null.");

    this.delegate = delegate;
    this.contextClassLoader = currentThread().getContextClassLoader();
  }

  @Override
  public void onThrowable(Throwable throwable) {
    ClassLoader oldClassLoader = currentThread().getContextClassLoader();
    currentThread().setContextClassLoader(contextClassLoader);
    try {
      delegate.onThrowable(throwable);
    } finally {
      currentThread().setContextClassLoader(oldClassLoader);
    }
  }

  @Override
  public STATE onBodyPartReceived(HttpResponseBodyPart httpResponseBodyPart) throws Exception {
    ClassLoader oldClassLoader = currentThread().getContextClassLoader();
    currentThread().setContextClassLoader(contextClassLoader);
    try {
      return delegate.onBodyPartReceived(httpResponseBodyPart);
    } finally {
      currentThread().setContextClassLoader(oldClassLoader);
    }
  }

  @Override
  public STATE onStatusReceived(HttpResponseStatus httpResponseStatus) throws Exception {
    ClassLoader oldClassLoader = currentThread().getContextClassLoader();
    currentThread().setContextClassLoader(contextClassLoader);
    try {
      return delegate.onStatusReceived(httpResponseStatus);
    } finally {
      currentThread().setContextClassLoader(oldClassLoader);
    }
  }

  @Override
  public STATE onHeadersReceived(HttpResponseHeaders httpResponseHeaders) throws Exception {
    ClassLoader oldClassLoader = currentThread().getContextClassLoader();
    currentThread().setContextClassLoader(contextClassLoader);
    try {
      return delegate.onHeadersReceived(httpResponseHeaders);
    } finally {
      currentThread().setContextClassLoader(oldClassLoader);
    }
  }

  @Override
  public T onCompleted() throws Exception {
    ClassLoader oldClassLoader = currentThread().getContextClassLoader();
    currentThread().setContextClassLoader(contextClassLoader);
    try {
      return delegate.onCompleted();
    } finally {
      currentThread().setContextClassLoader(oldClassLoader);
    }
  }
}
