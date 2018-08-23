/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.service.http.impl.service.client.async;

import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mule.service.http.impl.AllureConstants.HttpFeature.HTTP_SERVICE;
import static org.mule.service.http.impl.AllureConstants.HttpFeature.HttpStory.STREAMING;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.CompletableFuture;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.mule.runtime.api.util.Reference;
import org.mule.runtime.http.api.domain.entity.HttpEntity;
import org.mule.runtime.http.api.domain.message.request.HttpRequest;
import org.mule.runtime.http.api.domain.message.response.HttpResponse;
import org.mule.service.http.impl.service.client.MuleBodyDeferringAsyncHandler;
import org.mule.tck.junit4.AbstractMuleTestCase;

import com.ning.http.client.AsyncHandler;
import com.ning.http.client.HttpResponseStatus;
import com.ning.http.client.Response;
import com.ning.http.client.providers.grizzly.GrizzlyResponseBodyPart;

import io.qameta.allure.Feature;
import io.qameta.allure.Story;

@RunWith(MockitoJUnitRunner.class)
@Feature(HTTP_SERVICE)
@Story(STREAMING)
public class AsyncHandlerTestCase extends AbstractMuleTestCase {

  @Mock
  private HttpRequest request;

  @Mock
  private HttpEntity entity;

  @Mock
  private InputStream content;

  @Before
  public void setup() {
    when(request.getEntity()).thenReturn(entity);
    when(entity.getContent()).thenReturn(content);
  }

  @Test
  public void closeRequestContentOnCompleteWithResponseAsyncHandler() throws Exception {
    testCloseRequestContentOnCompletWith(new ResponseAsyncHandler(request, new CompletableFuture<>()));
  }

  @Test
  public void closeRequestContentOnCompleteWithDeferring() throws Exception {
    testCloseRequestContentOnCompletWith(new MuleBodyDeferringAsyncHandler(request, new ByteArrayOutputStream()));
  }

  private void testCloseRequestContentOnCompletWith(AsyncHandler<Response> handler) throws Exception, IOException {
    handler.onStatusReceived(mock(HttpResponseStatus.class, RETURNS_DEEP_STUBS));
    GrizzlyResponseBodyPart bodyPart = mock(GrizzlyResponseBodyPart.class, RETURNS_DEEP_STUBS);
    when(bodyPart.isLast()).thenReturn(true);
    handler.onCompleted();
    verify(content).close();
  }

}
