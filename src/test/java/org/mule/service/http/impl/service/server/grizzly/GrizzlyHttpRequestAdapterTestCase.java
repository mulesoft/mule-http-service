/*
 * Copyright 2023 Salesforce, Inc. All rights reserved.
 */
package org.mule.service.http.impl.service.server.grizzly;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import org.mule.runtime.http.api.domain.message.request.HttpRequest;
import org.mule.tck.junit4.AbstractMuleTestCase;

import java.net.InetSocketAddress;
import java.net.URI;

import org.glassfish.grizzly.filterchain.FilterChainContext;
import org.glassfish.grizzly.http.HttpContent;
import org.glassfish.grizzly.http.HttpRequestPacket;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class GrizzlyHttpRequestAdapterTestCase extends AbstractMuleTestCase {

  @Rule
  public ExpectedException expectedException = ExpectedException.none();

  private HttpRequest request;
  private HttpRequestPacket requestPacket = mock(HttpRequestPacket.class);

  @Before
  public void setUp() {
    HttpContent content = mock(HttpContent.class, RETURNS_DEEP_STUBS);
    when(content.isLast()).thenReturn(true);
    when(requestPacket.getRequestURI()).thenReturn("/song");

    request = new GrizzlyHttpRequestAdapter(mock(FilterChainContext.class), content, requestPacket,
                                            new InetSocketAddress("hidden", 29));
  }

  @Test
  public void hidesHostWhenFailing() {
    when(requestPacket.getQueryString()).thenReturn("id=0618%");

    expectedException.expect(IllegalArgumentException.class);
    expectedException.expectMessage(is("Malformed URI: /song?id=0618%"));
    request.getUri();
  }

  @Test
  public void providesCorrectUri() {
    when(requestPacket.getQueryString()).thenReturn("id=0618");

    URI uri = request.getUri();
    assertThat(uri.getHost(), is("hidden"));
    assertThat(uri.getPort(), is(29));
    assertThat(uri.getPath(), is("/song"));
    assertThat(uri.getQuery(), is("id=0618"));
  }

}
