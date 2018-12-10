/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.service.http.impl.service.client;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mule.service.http.impl.AllureConstants.HttpFeature.HTTP_SERVICE;

import org.mule.runtime.api.exception.MuleRuntimeException;
import org.mule.service.http.impl.service.HttpMessageLogger;
import org.mule.tck.junit4.AbstractMuleTestCase;
import org.mule.tck.size.SmallTest;

import org.glassfish.grizzly.filterchain.FilterChainBuilder;
import org.glassfish.grizzly.http.HttpCodecFilter;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import io.qameta.allure.Feature;

@RunWith(MockitoJUnitRunner.class)
@SmallTest
@Feature(HTTP_SERVICE)
public class LoggerTransportCustomizerTestCase extends AbstractMuleTestCase {

  @Mock
  private FilterChainBuilder mockFilterChainBuilder;

  @Mock(answer = Answers.RETURNS_DEEP_STUBS)
  private HttpCodecFilter mockHttpCodeFilter;

  @Mock
  private MuleRuntimeException mockMuleRuntimeException;

  @Rule
  public ExpectedException expectedException = ExpectedException.none();

  private LoggerTransportCustomizer loggerTransportCustomizer = new LoggerTransportCustomizer("config");

  @Test
  public void httpMessageLoggerIsAdded() {
    when(mockFilterChainBuilder.get(anyInt())).thenReturn(mockHttpCodeFilter);
    loggerTransportCustomizer.customize(null, mockFilterChainBuilder);
    verify(mockHttpCodeFilter.getMonitoringConfig()).addProbes(isA(HttpMessageLogger.class));
  }

  @Test
  public void noHttpCodeFilterFound() {
    when(mockFilterChainBuilder.get(anyInt())).thenThrow(mockMuleRuntimeException);
    expectedException.expect(MuleRuntimeException.class);
    loggerTransportCustomizer.customize(null, mockFilterChainBuilder);
  }

}
