/*
 * Copyright 2023 Salesforce, Inc. All rights reserved.
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.service.http.impl.service.server;

import static java.util.Optional.empty;
import static java.util.Optional.of;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.IsEqual.equalTo;

import static org.junit.jupiter.api.Assertions.assertThrows;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.mule.runtime.http.api.server.HttpServer;
import org.mule.runtime.http.api.server.HttpServerFactory;
import org.mule.runtime.http.api.server.ServerNotFoundException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatcher;

public class ContextHttpServerFactoryAdapterTestCase {

  private static final String SERVER_CONTEXT = "context";
  private static final String SERVER_PARENT_CONTEXT = "parentContext";
  private static final String SERVER_ID = "serverId";
  private static final long TEST_SHUTDOWN_TIMEOUT = 50;

  private ContextHttpServerFactory delegateFactory;
  private HttpServer mockedServer;

  @BeforeEach
  public void setUp() {
    delegateFactory = mock(ContextHttpServerFactory.class);
    mockedServer = mock(HttpServer.class);
  }

  @Test
  void serverRegisteredWithContext() throws Exception {
    mockServerRegisteredWith(SERVER_CONTEXT, SERVER_ID);
    HttpServerFactory httpServerFactory =
        new ContextHttpServerFactoryAdapter(SERVER_CONTEXT, empty(),
                                            delegateFactory, () -> TEST_SHUTDOWN_TIMEOUT);
    assertThat(httpServerFactory.lookup(SERVER_ID), equalTo(mockedServer));
  }

  @Test
  void serverRegisteredWithParentContext() throws Exception {
    mockServerRegisteredWith(SERVER_PARENT_CONTEXT, SERVER_ID);
    HttpServerFactory httpServerFactory = new ContextHttpServerFactoryAdapter(SERVER_CONTEXT, of(SERVER_PARENT_CONTEXT),
                                                                              delegateFactory, () -> TEST_SHUTDOWN_TIMEOUT);
    assertThat(httpServerFactory.lookup(SERVER_ID), equalTo(mockedServer));
  }

  @Test
  void serverInDifferentParentContextNotFound() throws Exception {
    mockServerRegisteredWith("OTHER_PARENT", SERVER_ID);
    HttpServerFactory httpServerFactory = new ContextHttpServerFactoryAdapter(SERVER_CONTEXT, of(SERVER_PARENT_CONTEXT),
                                                                              delegateFactory, () -> TEST_SHUTDOWN_TIMEOUT);
    assertThrows(ServerNotFoundException.class, () -> httpServerFactory.lookup(SERVER_ID));
  }

  @Test
  void serverInDifferentContextNotFound() throws Exception {
    mockServerRegisteredWith("OTHER_CONTEXT", SERVER_ID);
    HttpServerFactory httpServerFactory =
        new ContextHttpServerFactoryAdapter(SERVER_CONTEXT, empty(),
                                            delegateFactory, () -> TEST_SHUTDOWN_TIMEOUT);
    assertThrows(ServerNotFoundException.class, () -> httpServerFactory.lookup(SERVER_ID));
  }

  private void mockServerRegisteredWith(String context, String serverId) throws Exception {
    when(delegateFactory.lookup(any(ServerIdentifier.class))).thenThrow(new ServerNotFoundException(""));
    when(delegateFactory.lookup(aServerIdentifierWith(context, serverId))).thenReturn(mockedServer);
  }

  private ServerIdentifier aServerIdentifierWith(String context, String name) {

    return argThat(
                   new ArgumentMatcher<ServerIdentifier>() {

                     private final ServerIdentifier expectedId = new ServerIdentifier(context, name);

                     @Override
                     public boolean matches(ServerIdentifier argument) {
                       return expectedId.equals(argument);
                     }
                   });
  }

}
