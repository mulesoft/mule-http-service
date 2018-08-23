/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.service.http.impl.service.client.async;

import static com.ning.http.client.AsyncHandler.STATE.ABORT;
import static com.ning.http.client.AsyncHandler.STATE.CONTINUE;
import static org.hamcrest.Matchers.any;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mule.service.http.impl.AllureConstants.HttpFeature.HTTP_SERVICE;
import static org.mule.service.http.impl.AllureConstants.HttpFeature.HttpStory.STREAMING;
import org.mule.runtime.api.util.Reference;
import org.mule.runtime.http.api.domain.entity.HttpEntity;
import org.mule.runtime.http.api.domain.message.request.HttpRequest;
import org.mule.runtime.http.api.domain.message.response.HttpResponse;
import org.mule.tck.junit4.AbstractMuleTestCase;
import org.mule.tck.probe.JUnitProbe;
import org.mule.tck.probe.PollingProber;

import com.ning.http.client.HttpResponseStatus;
import com.ning.http.client.providers.grizzly.GrizzlyResponseBodyPart;

import java.io.InputStream;
import java.io.PipedInputStream;
import java.util.concurrent.CompletableFuture;

import io.qameta.allure.Feature;
import io.qameta.allure.Story;

import org.glassfish.grizzly.Connection;
import org.glassfish.grizzly.http.HttpContent;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
@Feature(HTTP_SERVICE)
@Story(STREAMING)
public class ResponseBodyDeferringAsyncHandlerTestCase extends AbstractMuleTestCase {

  @Mock
  private HttpRequest request;

  @Mock
  private HttpEntity entity;

  @Mock
  private InputStream content;

  private static final int PROBE_TIMEOUT = 5000;
  private static final int POLL_DELAY = 300;
  private static final int BUFFER_SIZE = 1024;

  @Before
  public void setup() {
    when(request.getEntity()).thenReturn(entity);
    when(entity.getContent()).thenReturn(content);
  }

  @Test
  public void closeRequestContentOnComplete() throws Exception {
    CompletableFuture<HttpResponse> future = new CompletableFuture<>();
    Reference<InputStream> responseContent = new Reference<>();
    ResponseBodyDeferringAsyncHandler handler =
        new ResponseBodyDeferringAsyncHandler(request, future, BUFFER_SIZE);
    handler.onStatusReceived(mock(HttpResponseStatus.class, RETURNS_DEEP_STUBS));
    GrizzlyResponseBodyPart bodyPart = mock(GrizzlyResponseBodyPart.class, RETURNS_DEEP_STUBS);
    when(bodyPart.isLast()).thenReturn(true);
    future.whenComplete((response, exception) -> responseContent.set(response.getEntity().getContent()));
    handler.onCompleted();
    verify(content).close();
  }

  @Test
  public void doesNotStreamWhenPossible() throws Exception {
    CompletableFuture<HttpResponse> future = new CompletableFuture<>();
    Reference<InputStream> responseContent = new Reference<>();
    ResponseBodyDeferringAsyncHandler handler =
        new ResponseBodyDeferringAsyncHandler(request, future, BUFFER_SIZE);
    handler.onStatusReceived(mock(HttpResponseStatus.class, RETURNS_DEEP_STUBS));
    GrizzlyResponseBodyPart bodyPart = mock(GrizzlyResponseBodyPart.class, RETURNS_DEEP_STUBS);
    when(bodyPart.isLast()).thenReturn(true);
    future.whenComplete((response, exception) -> responseContent.set(response.getEntity().getContent()));

    assertThat(handler.onBodyPartReceived(bodyPart), is(CONTINUE));

    new PollingProber(PROBE_TIMEOUT, POLL_DELAY).check(new JUnitProbe() {

      @Override
      protected boolean test() throws Exception {
        assertThat(responseContent.get(), not(nullValue()));
        assertThat(responseContent.get(), not(instanceOf(PipedInputStream.class)));
        return true;
      }
    });
  }

  @Test
  public void streamsWhenRequired() throws Exception {
    CompletableFuture<HttpResponse> future = new CompletableFuture<>();
    Reference<InputStream> responseContent = new Reference<>();
    ResponseBodyDeferringAsyncHandler handler =
        new ResponseBodyDeferringAsyncHandler(request, future, BUFFER_SIZE);
    handler.onStatusReceived(mock(HttpResponseStatus.class, RETURNS_DEEP_STUBS));
    GrizzlyResponseBodyPart bodyPart = mock(GrizzlyResponseBodyPart.class, RETURNS_DEEP_STUBS);
    when(bodyPart.isLast()).thenReturn(false);
    future.whenComplete((response, exception) -> responseContent.set(response.getEntity().getContent()));

    assertThat(handler.onBodyPartReceived(bodyPart), is(CONTINUE));

    new PollingProber(PROBE_TIMEOUT, POLL_DELAY).check(new JUnitProbe() {

      @Override
      protected boolean test() throws Exception {
        assertThat(responseContent.get(), not(nullValue()));
        assertThat(responseContent.get(), instanceOf(PipedInputStream.class));
        return true;
      }
    });
  }

  @Test
  public void abortsWhenPipeIsClosed() throws Exception {
    CompletableFuture<HttpResponse> future = new CompletableFuture<>();
    ResponseBodyDeferringAsyncHandler handler =
        new ResponseBodyDeferringAsyncHandler(request, future, BUFFER_SIZE);
    handler.onStatusReceived(mock(HttpResponseStatus.class, RETURNS_DEEP_STUBS));
    GrizzlyResponseBodyPart bodyPart = spy(new GrizzlyResponseBodyPart(mock(HttpContent.class), mock(Connection.class)));
    when(bodyPart.isLast()).thenReturn(false);
    doReturn("You will call me Snowball because my fur is pretty and white.".getBytes()).when(bodyPart).getBodyPartBytes();
    handler.onBodyPartReceived(bodyPart);
    handler.closeOut();
    assertThat(handler.onBodyPartReceived(bodyPart), is(ABORT));
  }

}
