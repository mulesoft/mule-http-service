/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.service.http.impl.service.client.async;

import static java.lang.Thread.currentThread;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.when;
import static org.mule.runtime.core.api.util.ClassUtils.withContextClassLoader;
import org.mule.runtime.api.util.Reference;
import org.mule.tck.junit4.AbstractMuleTestCase;

import com.ning.http.client.AsyncHandler;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class PreservingClassLoaderAsyncHandlerTestCase extends AbstractMuleTestCase {

  @Mock
  private AsyncHandler<Integer> delegate;

  @Mock
  private ClassLoader mockClassLoader;

  private PreservingClassLoaderAsyncHandler<Integer> asyncHandler;
  private ClassLoader classLoaderOnCreation;

  @Before
  public void setup() {
    classLoaderOnCreation = currentThread().getContextClassLoader();
    asyncHandler = new PreservingClassLoaderAsyncHandler<>(delegate);
  }

  @Test
  public void creationClassLoaderIsPreservedOnCompleted() throws Exception {
    Reference<ClassLoader> classLoaderOnCompleted = new Reference<>();
    when(delegate.onCompleted()).then(invocation -> {
      classLoaderOnCompleted.set(currentThread().getContextClassLoader());
      return doCallRealMethod();
    });

    // Call the method with other classloader
    withContextClassLoader(mockClassLoader, asyncHandler::onCompleted);
    assertThat(classLoaderOnCompleted.get(), is(classLoaderOnCreation));
  }

  @Test
  public void creationClassLoaderIsPreservedOnThrowable() {
    Reference<ClassLoader> classLoaderOnThrowable = new Reference<>();
    doAnswer(invocation -> {
      classLoaderOnThrowable.set(currentThread().getContextClassLoader());
      return doCallRealMethod();
    }).when(delegate).onThrowable(any(Throwable.class));

    // Call the method with other classloader
    withContextClassLoader(mockClassLoader, () -> asyncHandler.onThrowable(new Throwable()));
    assertThat(classLoaderOnThrowable.get(), is(classLoaderOnCreation));
  }

  @Test
  public void creationClassLoaderIsPreservedOnBodyPartReceived() throws Exception {
    Reference<ClassLoader> classLoaderOnBodyPartReceived = new Reference<>();
    when(delegate.onBodyPartReceived(any())).then(invocation -> {
      classLoaderOnBodyPartReceived.set(currentThread().getContextClassLoader());
      return null;
    });

    // Call the method with other classloader
    withContextClassLoader(mockClassLoader, () -> asyncHandler.onBodyPartReceived(null));
    assertThat(classLoaderOnBodyPartReceived.get(), is(classLoaderOnCreation));
  }

  @Test
  public void creationClassLoaderIsPreservedOnStatusReceived() throws Exception {
    Reference<ClassLoader> classLoaderOnStatusReceived = new Reference<>();
    when(delegate.onStatusReceived(any())).then(invocation -> {
      classLoaderOnStatusReceived.set(currentThread().getContextClassLoader());
      return null;
    });

    // Call the method with other classloader
    withContextClassLoader(mockClassLoader, () -> asyncHandler.onStatusReceived(null));
    assertThat(classLoaderOnStatusReceived.get(), is(classLoaderOnCreation));
  }

  @Test
  public void creationClassLoaderIsPreservedOnHeadersReceived() throws Exception {
    Reference<ClassLoader> classLoaderOnHeadersReceived = new Reference<>();
    when(delegate.onHeadersReceived(any())).then(invocation -> {
      classLoaderOnHeadersReceived.set(currentThread().getContextClassLoader());
      return null;
    });

    // Call the method with other classloader
    withContextClassLoader(mockClassLoader, () -> asyncHandler.onHeadersReceived(null));
    assertThat(classLoaderOnHeadersReceived.get(), is(classLoaderOnCreation));
  }
}
