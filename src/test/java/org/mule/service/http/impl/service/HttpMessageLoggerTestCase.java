/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.service.http.impl.service;

import static java.lang.Thread.currentThread;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;
import static org.mule.service.http.impl.service.HttpMessageLogger.LoggerType.REQUESTER;
import static org.slf4j.MDC.getCopyOfContextMap;
import org.mule.runtime.api.util.Reference;
import org.mule.tck.size.SmallTest;

import java.util.Map;

import io.qameta.allure.Issue;
import org.apache.commons.collections.map.SingletonMap;
import org.glassfish.grizzly.Buffer;
import org.glassfish.grizzly.Connection;
import org.glassfish.grizzly.attributes.AttributeHolder;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.slf4j.Logger;

@RunWith(MockitoJUnitRunner.class)
@SmallTest
@Issue("MULE-19243")
public class HttpMessageLoggerTestCase {

  @Mock
  private ClassLoader classLoader;

  @Mock
  private Logger logger;

  @Mock
  private Buffer buffer;

  @Mock
  private Connection connection;

  @Mock
  private AttributeHolder connectionAttributeHolder;

  private Reference<ClassLoader> onLogClassLoader;

  private Reference<Map<String, String>> onLogMDC;

  private HttpMessageLogger httpMessageLogger;

  @Before
  public void setup() {
    initMocks(HttpMessageLoggerTestCase.class);

    when(connectionAttributeHolder.getAttribute(eq("mdc"))).thenReturn(new SingletonMap("mdcKey", "mdcValue"));
    when(connection.getAttributes()).thenReturn(connectionAttributeHolder);

    doAnswer(invocation -> {
      onLogClassLoader.set(currentThread().getContextClassLoader());
      onLogMDC.set(getCopyOfContextMap());
      return null;
    }).when(logger).debug(anyString(), any(), any());
    when(logger.isDebugEnabled()).thenReturn(true);

    onLogClassLoader = new Reference<>();
    onLogMDC = new Reference<>();
    httpMessageLogger = new HttpMessageLogger(REQUESTER, classLoader, logger);
  }

  @Test
  public void mdcIsPreservedOnDataReceived() {
    httpMessageLogger.onDataReceivedEvent(connection, buffer);
    assertThat(onLogMDC.get().get("mdcKey"), is("mdcValue"));
  }

  @Test
  public void classLoaderIsPreservedOnDataReceived() {
    httpMessageLogger.onDataReceivedEvent(connection, buffer);
    assertThat(onLogClassLoader.get(), is(classLoader));
  }

  @Test
  public void mdcIsPreservedOnDataSent() {
    httpMessageLogger.onDataSentEvent(connection, buffer);
    assertThat(onLogMDC.get().get("mdcKey"), is("mdcValue"));
  }

  @Test
  public void classLoaderIsPreservedOnDataSent() {
    httpMessageLogger.onDataSentEvent(connection, buffer);
    assertThat(onLogClassLoader.get(), is(classLoader));
  }
}
