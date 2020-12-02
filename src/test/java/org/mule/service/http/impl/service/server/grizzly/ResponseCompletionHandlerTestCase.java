/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.service.http.impl.service.server.grizzly;

import static java.lang.Thread.currentThread;
import static java.util.Collections.singletonList;
import static org.glassfish.grizzly.http.Protocol.HTTP_1_1;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mule.runtime.http.api.HttpHeaders.Names.CONNECTION;
import static org.mule.service.http.impl.AllureConstants.HttpFeature.HTTP_SERVICE;
import static org.mule.service.http.impl.AllureConstants.HttpFeature.HttpStory.RESPONSES;

import org.glassfish.grizzly.http.ProcessingState;
import org.junit.Test;
import org.mule.runtime.http.api.domain.entity.ByteArrayHttpEntity;
import org.mule.runtime.http.api.domain.entity.HttpEntity;
import org.mule.runtime.http.api.domain.message.response.HttpResponse;

import org.junit.Before;

import io.qameta.allure.Feature;
import io.qameta.allure.Story;

import java.util.Collection;

@Feature(HTTP_SERVICE)
@Story(RESPONSES)
public class ResponseCompletionHandlerTestCase extends BaseResponseCompletionHandlerTestCase {

  @Before
  public void setUp() {
    HttpEntity entity = new ByteArrayHttpEntity(new byte[1]);
    responseMock = mock(HttpResponse.class);
    when(request.getProtocol()).thenReturn(HTTP_1_1);
    when(responseMock.getEntity()).thenReturn(entity);
    when(ctx.getConnection()).thenReturn(connection);
    when(connection.getMemoryManager()).thenReturn(null);
    when(ctx.getMemoryManager()).thenReturn(null);
  }

  @Override
  protected BaseResponseCompletionHandler getHandler() {
    return new ResponseCompletionHandler(ctx, currentThread().getContextClassLoader(), request, responseMock, callback);
  }

  @Test
  public void keepAliveConnection() {
    final Collection<String> headerName = singletonList(CONNECTION);
    when(responseMock.getHeaderNames()).thenReturn(headerName);
    when(responseMock.getHeaderValue(CONNECTION)).thenReturn(KEEP_ALIVE);
    assertThat(getHandler().getHttpResponsePacket().getHeader(CONNECTION), equalTo(KEEP_ALIVE));
  }

  @Test
  public void cLoseConnection() {
    final Collection<String> headerName = singletonList(CONNECTION);
    when(responseMock.getHeaderNames()).thenReturn(headerName);
    when(responseMock.getHeaderValue(CONNECTION)).thenReturn(CLOSE);
    when(request.getProcessingState()).thenReturn(new ProcessingState());
    assertThat(getHandler().getHttpResponsePacket().getHeader(CONNECTION), equalTo(CLOSE));
  }

}
