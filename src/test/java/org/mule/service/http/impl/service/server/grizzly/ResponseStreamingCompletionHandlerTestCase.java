/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.service.http.impl.service.server.grizzly;

import static java.lang.Thread.currentThread;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.rules.ExpectedException.none;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mule.runtime.http.api.HttpHeaders.Names.CONNECTION;
import static org.mule.service.http.impl.AllureConstants.HttpFeature.HTTP_SERVICE;
import static org.mule.service.http.impl.AllureConstants.HttpFeature.HttpStory.RESPONSES;

import org.glassfish.grizzly.http.ProcessingState;
import org.junit.Rule;
import org.junit.Test;

import org.mule.runtime.api.exception.MuleRuntimeException;
import org.mule.runtime.api.util.MultiMap;
import org.mule.runtime.http.api.domain.entity.InputStreamHttpEntity;
import org.mule.runtime.http.api.domain.message.response.HttpResponse;

import org.glassfish.grizzly.Transport;
import org.junit.Before;

import java.io.IOException;
import java.io.InputStream;

import io.qameta.allure.Feature;
import io.qameta.allure.Story;
import org.junit.rules.ExpectedException;

@Feature(HTTP_SERVICE)
@Story(RESPONSES)
public class ResponseStreamingCompletionHandlerTestCase extends BaseResponseCompletionHandlerTestCase {

  private ResponseStreamingCompletionHandler handler;
  private InputStream mockStream;

  @Rule
  public ExpectedException exception = none();

  @Before
  public void setUp() {
    when(ctx.getConnection()).thenReturn(connection);
    when(connection.getTransport()).thenReturn(mock(Transport.class, RETURNS_DEEP_STUBS));
    mockStream = mock(InputStream.class);
    responseMock = HttpResponse.builder().entity(new InputStreamHttpEntity(mockStream)).build();
    handler = new ResponseStreamingCompletionHandler(ctx,
                                                     currentThread().getContextClassLoader(),
                                                     request,
                                                     responseMock,
                                                     callback);
  }

  @Override
  protected BaseResponseCompletionHandler getHandler() {
    return handler;
  }

  @Test
  public void keepAliveConnection() {
    final MultiMap<String, String> headers = new MultiMap<>();
    headers.put(CONNECTION, KEEP_ALIVE);
    responseMock = HttpResponse.builder().entity(new InputStreamHttpEntity(mockStream)).headers(headers).build();

    handler = new ResponseStreamingCompletionHandler(ctx,
                                                     currentThread().getContextClassLoader(),
                                                     request,
                                                     responseMock,
                                                     callback);
    assertThat(handler.getHttpResponsePacket().getHeader(CONNECTION), equalTo(KEEP_ALIVE));
  }

  @Test
  public void cLoseConnection() {
    final MultiMap<String, String> headers = new MultiMap<>();
    headers.put(CONNECTION, CLOSE);
    responseMock = HttpResponse.builder().entity(new InputStreamHttpEntity(mockStream)).headers(headers).build();
    when(request.getProcessingState()).thenReturn(new ProcessingState());
    handler = new ResponseStreamingCompletionHandler(ctx,
                                                     currentThread().getContextClassLoader(),
                                                     request,
                                                     responseMock,
                                                     callback);
    assertThat(getHandler().getHttpResponsePacket().getHeader(CONNECTION), equalTo(CLOSE));
  }

  @Test
  public void completionHandlerFailsIfAReadOperationThrowsAMuleRuntimeException() throws IOException {
    responseMock = HttpResponse.builder().entity(new InputStreamHttpEntity(mockStream)).build();
    when(request.getProcessingState()).thenReturn(new ProcessingState());
    handler = spy(new ResponseStreamingCompletionHandler(ctx,
                                                         currentThread().getContextClassLoader(),
                                                         request,
                                                         responseMock,
                                                         callback));

    when(mockStream.read(any(), anyInt(), anyInt())).thenThrow(new MuleRuntimeException(new NullPointerException()));
    handler.sendInputStreamChunk();

    verify(getHandler(), times(1)).failed(any(Throwable.class));
  }
}
