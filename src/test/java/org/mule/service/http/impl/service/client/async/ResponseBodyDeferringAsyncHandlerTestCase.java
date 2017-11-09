/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.service.http.impl.service.client.async;

import static com.ning.http.client.AsyncHandler.STATE.ABORT;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;
import static org.mule.service.http.impl.AllureConstants.HttpFeature.HTTP_SERVICE;
import static org.mule.service.http.impl.AllureConstants.HttpFeature.HttpStory.STREAMING;
import org.mule.runtime.http.api.domain.message.response.HttpResponse;
import org.mule.tck.junit4.AbstractMuleTestCase;

import com.ning.http.client.HttpResponseStatus;
import com.ning.http.client.providers.grizzly.GrizzlyResponseBodyPart;

import java.util.concurrent.CompletableFuture;

import io.qameta.allure.Feature;
import io.qameta.allure.Story;
import org.glassfish.grizzly.Connection;
import org.glassfish.grizzly.http.HttpContent;
import org.junit.Test;

@Feature(HTTP_SERVICE)
@Story(STREAMING)
public class ResponseBodyDeferringAsyncHandlerTestCase extends AbstractMuleTestCase {

//  @Test
//  public void doesNotStreamWhenPossible() throws Exception {
//    CompletableFuture<HttpResponse> future = new CompletableFuture<>();
//    ResponseBodyDeferringAsyncHandler handler = new ResponseBodyDeferringAsyncHandler(future, 1024);
//    handler.onStatusReceived(mock(HttpResponseStatus.class, RETURNS_DEEP_STUBS));
//    GrizzlyResponseBodyPart bodyPart = mock(GrizzlyResponseBodyPart.class, RETURNS_DEEP_STUBS);
//    when(bodyPart.isLast()).thenReturn(true);
//    assertThat(handler.onBodyPartReceived(bodyPart), is(CONTINUE));
//
//    Reference<InputStream> responseContent
//
//    future.whenComplete((response, exception) -> {
//      InputStream content = response.getEntity().getContent();
//      assertThat(content, instanceOf(BufferInputStream.class));
//      try {
//        content.close();
//      } catch (IOException e) {
//        // Do nothing
//      }
//      done.release();
//    });
//
//    done.await(5000, MILLISECONDS);
//  }
//
//  @Test
//  public void streamsWhenRequired() throws Exception {
//    Latch done = new Latch();
//    CompletableFuture<HttpResponse> future = new CompletableFuture<>();
//    ResponseBodyDeferringAsyncHandler handler = new ResponseBodyDeferringAsyncHandler(future, 1024);
//    handler.onStatusReceived(mock(HttpResponseStatus.class, RETURNS_DEEP_STUBS));
//    GrizzlyResponseBodyPart bodyPart = mock(GrizzlyResponseBodyPart.class, RETURNS_DEEP_STUBS);
//    when(bodyPart.isLast()).thenReturn(false);
//
//    assertThat(handler.onBodyPartReceived(bodyPart), is(CONTINUE));
//
//    future.whenComplete((response, exception) -> {
//      InputStream content = response.getEntity().getContent();
//      assertThat(content, instanceOf(PipedInputStream.class));
//      try {
//        content.close();
//      } catch (IOException e) {
//        // Do nothing
//      }
//      done.release();
//    });
//
//    done.await(5000, MILLISECONDS);
//  }

  @Test
  public void abortsWhenPipeIsClosed() throws Exception {
    CompletableFuture<HttpResponse> future = new CompletableFuture<>();
    ResponseBodyDeferringAsyncHandler handler = new ResponseBodyDeferringAsyncHandler(future, 1024);
    handler.onStatusReceived(mock(HttpResponseStatus.class, RETURNS_DEEP_STUBS));
    GrizzlyResponseBodyPart bodyPart = spy(new GrizzlyResponseBodyPart(mock(HttpContent.class), mock(Connection.class)));
    when(bodyPart.isLast()).thenReturn(false);
    doReturn("You will call me Snowball because my fur is pretty and white.".getBytes()).when(bodyPart).getBodyPartBytes();
    handler.onBodyPartReceived(bodyPart);
    handler.closeOut();
    assertThat(handler.onBodyPartReceived(bodyPart), is(ABORT));
  }

}
