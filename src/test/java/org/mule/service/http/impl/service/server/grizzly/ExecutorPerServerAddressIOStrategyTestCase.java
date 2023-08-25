/*
 * Copyright 2023 Salesforce, Inc. All rights reserved.
 */
package org.mule.service.http.impl.service.server.grizzly;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.junit.Assert.assertThat;
import static org.mockito.ArgumentMatchers.matches;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.when;
import static org.mule.service.http.impl.AllureConstants.HttpFeature.HTTP_SERVICE;
import static org.mule.service.http.impl.service.server.grizzly.ExecutorPerServerAddressIOStrategy.DELEGATE_WRITES_IN_CONFIGURED_EXECUTOR;

import org.mule.runtime.http.api.server.ServerAddress;
import org.mule.tck.junit4.AbstractMuleTestCase;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.concurrent.Executor;

import org.glassfish.grizzly.Connection;
import org.glassfish.grizzly.IOEvent;
import org.glassfish.grizzly.IOStrategy;
import org.glassfish.grizzly.attributes.AttributeHolder;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import io.qameta.allure.Feature;

@RunWith(MockitoJUnitRunner.class)
@Feature(HTTP_SERVICE)
public class ExecutorPerServerAddressIOStrategyTestCase extends AbstractMuleTestCase {

  @Mock
  private ExecutorProvider executorProvider;
  @Mock
  private Connection connection;
  @Mock
  private Executor executor;
  @Mock
  private AttributeHolder attributeHolder;

  private IOStrategy ioStrategy;

  @Before
  public void before() throws UnknownHostException {
    ioStrategy = new ExecutorPerServerAddressIOStrategy(executorProvider);
    when(connection.getLocalAddress()).thenReturn(new InetSocketAddress(InetAddress.getLocalHost(), 80));
    when(executorProvider.getExecutor(any(ServerAddress.class))).thenReturn(executor);
    when(connection.getAttributes()).thenReturn(attributeHolder);
  }

  @Test
  public void acceptIOEventDoesNotUseExecutor() {
    assertThat(ioStrategy.getThreadPoolFor(connection, IOEvent.SERVER_ACCEPT), is(nullValue()));
  }

  @Test
  public void readIOEventDoesNotUseExecutor() {
    assertThat(ioStrategy.getThreadPoolFor(connection, IOEvent.READ), is(nullValue()));
  }

  @Test
  public void writeIOEventDoesNotUseExecutor() {
    assertThat(ioStrategy.getThreadPoolFor(connection, IOEvent.WRITE), is(nullValue()));
  }

  @Test
  public void closeIOEventDoesNotUseExecutor() {
    assertThat(ioStrategy.getThreadPoolFor(connection, IOEvent.CLOSED), is(nullValue()));
  }

  @Test
  public void acceptIOEventDoesNotUseExecutorWhenDelegatePropertyInTrue() {
    when(attributeHolder.getAttribute(matches(DELEGATE_WRITES_IN_CONFIGURED_EXECUTOR))).thenReturn(true);
    assertThat(ioStrategy.getThreadPoolFor(connection, IOEvent.SERVER_ACCEPT), is(nullValue()));
  }

  @Test
  public void readIOEventDoesNotUseExecutorWhenDelegatePropertyInTrue() {
    when(attributeHolder.getAttribute(matches(DELEGATE_WRITES_IN_CONFIGURED_EXECUTOR))).thenReturn(true);
    assertThat(ioStrategy.getThreadPoolFor(connection, IOEvent.READ), is(nullValue()));
  }

  @Test
  public void writeIOEventUsesExecutorWhenDelegatePropertyInTrue() {
    when(attributeHolder.getAttribute(matches(DELEGATE_WRITES_IN_CONFIGURED_EXECUTOR))).thenReturn(true);
    assertThat(ioStrategy.getThreadPoolFor(connection, IOEvent.WRITE), is(equalTo(executor)));
  }

  @Test
  public void closeIOEventDoesNotUseExecutorWhenDelegatePropertyInTrue() {
    when(attributeHolder.getAttribute(matches(DELEGATE_WRITES_IN_CONFIGURED_EXECUTOR))).thenReturn(true);
    assertThat(ioStrategy.getThreadPoolFor(connection, IOEvent.CLOSED), is(nullValue()));
  }

  @Test
  public void acceptIOEventDoesNotUseExecutorWhenDelegatePropertyInFalse() {
    when(attributeHolder.getAttribute(matches(DELEGATE_WRITES_IN_CONFIGURED_EXECUTOR))).thenReturn(false);
    assertThat(ioStrategy.getThreadPoolFor(connection, IOEvent.SERVER_ACCEPT), is(nullValue()));
  }

  @Test
  public void readIOEventDoesNotUseExecutorWhenDelegatePropertyInFalse() {
    when(attributeHolder.getAttribute(matches(DELEGATE_WRITES_IN_CONFIGURED_EXECUTOR))).thenReturn(false);
    assertThat(ioStrategy.getThreadPoolFor(connection, IOEvent.READ), is(nullValue()));
  }

  @Test
  public void writeIOEventDoesNotUseExecutorWhenDelegatePropertyInFalse() {
    when(attributeHolder.getAttribute(matches(DELEGATE_WRITES_IN_CONFIGURED_EXECUTOR))).thenReturn(false);
    assertThat(ioStrategy.getThreadPoolFor(connection, IOEvent.WRITE), is(nullValue()));
  }

  @Test
  public void closeIOEventDoesNotUseExecutorWhenDelegatePropertyInFalse() {
    when(attributeHolder.getAttribute(matches(DELEGATE_WRITES_IN_CONFIGURED_EXECUTOR))).thenReturn(false);
    assertThat(ioStrategy.getThreadPoolFor(connection, IOEvent.CLOSED), is(nullValue()));
  }

}
