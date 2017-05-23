/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.service.http.impl.service.server.grizzly;

import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mule.service.http.impl.service.AllureConstants.HttpFeature.HTTP_SERVICE;
import static org.mule.service.http.impl.service.AllureConstants.HttpFeature.HttpStory.RESPONSES;

import org.mule.runtime.http.api.domain.entity.InputStreamHttpEntity;
import org.mule.runtime.http.api.domain.message.response.HttpResponse;
import org.mule.service.http.impl.service.server.grizzly.BaseResponseCompletionHandler;
import org.mule.service.http.impl.service.server.grizzly.ResponseStreamingCompletionHandler;

import java.io.InputStream;

import org.glassfish.grizzly.Transport;
import ru.yandex.qatools.allure.annotations.Features;
import ru.yandex.qatools.allure.annotations.Stories;

@Features(HTTP_SERVICE)
@Stories(RESPONSES)
public class ResponseStreamingCompletionHandlerTestCase extends BaseResponseCompletionHandlerTestCase {

  private ResponseStreamingCompletionHandler handler;

  @Override
  public void setUp() {
    super.setUp();
    when(connection.getTransport()).thenReturn(mock(Transport.class, RETURNS_DEEP_STUBS));
    InputStream mockStream = mock(InputStream.class);
    handler = new ResponseStreamingCompletionHandler(ctx,
                                                     request,
                                                     HttpResponse.builder().setEntity(new InputStreamHttpEntity(mockStream))
                                                         .build(),

                                                     callback);
  }

  @Override
  protected BaseResponseCompletionHandler getHandler() {
    return handler;
  }
}
