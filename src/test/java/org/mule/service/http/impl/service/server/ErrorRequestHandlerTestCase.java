/*
 * Copyright 2023 Salesforce, Inc. All rights reserved.
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.service.http.impl.service.server;

import static java.util.Arrays.asList;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import org.mule.runtime.core.api.util.IOUtils;
import org.mule.runtime.http.api.domain.entity.InputStreamHttpEntity;
import org.mule.runtime.http.api.domain.message.response.HttpResponse;
import org.mule.runtime.http.api.domain.request.HttpRequestContext;
import org.mule.runtime.http.api.server.async.HttpResponseReadyCallback;
import org.mule.runtime.http.api.server.async.ResponseStatusCallback;
import org.mule.tck.junit4.AbstractMuleTestCase;

import java.util.Collection;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ErrorRequestHandlerTestCase extends AbstractMuleTestCase {

  private static final String SCRIPT = "/<script>alert('hello');</script>";
  private final HttpRequestContext context = mock(HttpRequestContext.class, RETURNS_DEEP_STUBS);
  private final HttpResponseReadyCallback responseReadyCallback = mock(HttpResponseReadyCallback.class);

  @Parameter
  public ErrorRequestHandler errorRequestHandler;

  @Parameters(name = "{0}")
  public static Collection<Object[]> parameters() {
    return asList(new Object[][] {
        {NoListenerRequestHandler.getInstance()},
        {NoMethodRequestHandler.getInstance()},
        {ServiceTemporarilyUnavailableListenerRequestHandler.getInstance()}
    });
  }

  @Before
  public void setUp() throws Exception {
    when(context.getRequest().getPath()).thenReturn(SCRIPT);
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
    errorRequestHandler.handleRequest(context, responseReadyCallback);
    assertThat(result[0], not(containsString(SCRIPT)));
  }
}
