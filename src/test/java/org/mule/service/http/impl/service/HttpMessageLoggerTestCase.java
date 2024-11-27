/*
 * Copyright 2023 Salesforce, Inc. All rights reserved.
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.service.http.impl.service;

import static org.mule.service.http.impl.service.HttpMessageLogger.LoggerType.REQUESTER;

import static java.lang.Thread.currentThread;
import static java.util.Collections.singletonMap;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;
import static org.slf4j.MDC.getCopyOfContextMap;

import org.mule.runtime.api.util.Reference;
import org.mule.tck.size.SmallTest;

import java.util.Map;

import org.glassfish.grizzly.Buffer;
import org.glassfish.grizzly.Connection;
import org.glassfish.grizzly.attributes.AttributeHolder;
import org.slf4j.Logger;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoJUnitRunner;
import org.mockito.junit.MockitoRule;

import io.qameta.allure.Issue;

@RunWith(MockitoJUnitRunner.class)
@SmallTest
@Issue("MULE-19243")
public class HttpMessageLoggerTestCase {

  @Rule
  public MockitoRule rule = MockitoJUnit.rule();

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
    when(connectionAttributeHolder.getAttribute(eq("mdc"))).thenReturn(singletonMap("mdcKey", "mdcValue"));
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
