/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */

package org.mule.service.http.impl.service.server;

import static java.lang.String.format;
import static org.apache.commons.text.StringEscapeUtils.escapeHtml4;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mule.service.http.impl.service.server.NoListenerRequestHandler.NO_LISTENER_ENTITY_FORMAT;

import org.mule.runtime.core.api.util.IOUtils;
import org.mule.runtime.http.api.domain.entity.InputStreamHttpEntity;
import org.mule.runtime.http.api.domain.message.response.HttpResponse;
import org.mule.runtime.http.api.domain.request.HttpRequestContext;
import org.mule.runtime.http.api.server.async.HttpResponseReadyCallback;
import org.mule.runtime.http.api.server.async.ResponseStatusCallback;
import org.mule.tck.junit4.AbstractMuleTestCase;

import org.junit.Before;
import org.junit.Test;


public class NoListenerRequestHandlerTestCase extends AbstractMuleTestCase {

  private final static String TEST_REQUEST_INVALID_URI = "http://localhost:8081/<script>alert('hello');</script>";
  private final HttpRequestContext context = mock(HttpRequestContext.class, RETURNS_DEEP_STUBS);
  private final HttpResponseReadyCallback responseReadyCallback = mock(HttpResponseReadyCallback.class);
  private NoListenerRequestHandler noListenerRequestHandler;

  @Before
  public void setUp() throws Exception {
    noListenerRequestHandler = NoListenerRequestHandler.getInstance();
    when(context.getRequest().getUri()).thenReturn(TEST_REQUEST_INVALID_URI);
  }

  @Test
  public void testInvalidEndpointWithSpecialCharacters() throws Exception {
    final String[] result = new String[1];
    doAnswer(invocation -> {
      HttpResponse response = (HttpResponse) invocation.getArguments()[0];
      InputStreamHttpEntity inputStreamHttpEntity = (InputStreamHttpEntity) response.getEntity();
      result[0] = IOUtils.toString(inputStreamHttpEntity.getContent());
      inputStreamHttpEntity.getContent().close();
      return null;
    }).when(responseReadyCallback).responseReady(any(HttpResponse.class), any(ResponseStatusCallback.class));
    noListenerRequestHandler.handleRequest(context, responseReadyCallback);
    assertThat(result[0], is(format(NO_LISTENER_ENTITY_FORMAT, escapeHtml4(TEST_REQUEST_INVALID_URI))));
  }
}
