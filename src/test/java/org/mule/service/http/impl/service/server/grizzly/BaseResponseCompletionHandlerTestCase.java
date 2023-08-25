/*
 * Copyright 2023 Salesforce, Inc. All rights reserved.
 */
package org.mule.service.http.impl.service.server.grizzly;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mule.runtime.api.connection.SourceRemoteConnectionException;
import org.mule.runtime.http.api.domain.message.response.HttpResponse;
import org.mule.runtime.http.api.server.async.ResponseStatusCallback;
import org.mule.tck.junit4.AbstractMuleTestCase;

import java.io.IOException;

import org.glassfish.grizzly.Connection;
import org.glassfish.grizzly.filterchain.FilterChainContext;
import org.glassfish.grizzly.http.HttpRequestPacket;
import org.junit.Test;

public abstract class BaseResponseCompletionHandlerTestCase extends AbstractMuleTestCase {

  private static final String ERROR = "Error";
  protected FilterChainContext ctx = mock(FilterChainContext.class);
  protected Connection connection = mock(Connection.class, RETURNS_DEEP_STUBS);
  protected HttpRequestPacket request = mock(HttpRequestPacket.class);
  protected ResponseStatusCallback callback = spy(ResponseStatusCallback.class);
  protected HttpResponse responseMock;
  protected static final String KEEP_ALIVE = "Keep-Alive";
  protected static final String CLOSE = "close";

  protected abstract BaseResponseCompletionHandler getHandler();

  @Test
  public void failedTaskAvoidsResponse() {
    when(connection.isOpen()).thenReturn(false);
    getHandler().failed(new IOException(ERROR));
    verify(callback, never()).responseSendFailure(any(Throwable.class));
    verify(callback, atLeastOnce()).onErrorSendingResponse(any(SourceRemoteConnectionException.class));
  }

  @Test
  public void cancelledTaskResponse() {
    when(connection.isOpen()).thenReturn(true);
    getHandler().cancelled();
    verify(callback, atLeastOnce()).responseSendFailure(any(Throwable.class));
  }

}
