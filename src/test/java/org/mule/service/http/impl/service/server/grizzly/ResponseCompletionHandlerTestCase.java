/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.service.http.impl.service.server.grizzly;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mule.service.http.impl.AllureConstants.HttpFeature.HTTP_SERVICE;
import static org.mule.service.http.impl.AllureConstants.HttpFeature.HttpStory.RESPONSES;
import org.mule.runtime.http.api.domain.entity.ByteArrayHttpEntity;
import org.mule.runtime.http.api.domain.entity.HttpEntity;
import org.mule.runtime.http.api.domain.message.response.HttpResponse;

import org.junit.Before;
import io.qameta.allure.Feature;
import io.qameta.allure.Story;

@Feature(HTTP_SERVICE)
@Story(RESPONSES)
public class ResponseCompletionHandlerTestCase extends BaseResponseCompletionHandlerTestCase {

  HttpResponse responseMock;

  @Before
  public void setUp() {
    HttpEntity entity = new ByteArrayHttpEntity(new byte[1]);
    responseMock = mock(HttpResponse.class);
    when(responseMock.getEntity()).thenReturn(entity);
    when(ctx.getConnection()).thenReturn(connection);
    when(connection.getMemoryManager()).thenReturn(null);
    when(ctx.getMemoryManager()).thenReturn(null);
  }

  @Override
  protected BaseResponseCompletionHandler getHandler() {
    return new ResponseCompletionHandler(ctx, request, responseMock, callback);
  }

}
