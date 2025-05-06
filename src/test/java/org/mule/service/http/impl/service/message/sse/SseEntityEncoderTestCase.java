/*
 * Copyright 2023 Salesforce, Inc. All rights reserved.
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.service.http.impl.service.message.sse;

import static org.mule.service.http.impl.AllureConstants.HttpFeature.HTTP_SERVICE;
import static org.mule.service.http.impl.AllureConstants.HttpFeature.HttpStory.SSE;
import static org.mule.service.http.impl.AllureConstants.HttpFeature.HttpStory.SSE_ENDPOINT;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThrows;
import static org.junit.internal.matchers.ThrowableMessageMatcher.hasMessage;

import org.mule.runtime.http.api.sse.ServerSentEvent;
import org.mule.service.http.impl.service.message.sse.ServerSentEventImpl;
import org.mule.service.http.impl.service.message.sse.SseEntityEncoder;
import org.mule.tck.junit4.AbstractMuleTestCase;

import java.io.IOException;
import java.io.StringWriter;

import io.qameta.allure.Feature;
import io.qameta.allure.Story;
import org.junit.Test;

@Feature(HTTP_SERVICE)
@Story(SSE)
@Story(SSE_ENDPOINT)
public class SseEntityEncoderTestCase extends AbstractMuleTestCase {

  public static final String THREE_LINES_DATA = """
      line1
      line2
      line3
      """;
  private final SseEntityEncoder encoder = new SseEntityEncoder();
  private final StringWriter writer = new StringWriter();

  @Test
  public void singleLineNoOptionals() throws Exception {
    ServerSentEvent event = new ServerSentEventImpl("theName", "oneLineData", null, null);
    encoder.writeTo(writer, event);
    assertThat(writer.toString(), is("""
        event: theName
        data: oneLineData

        """));
  }

  @Test
  public void threeLinesNoOptionals() throws Exception {
    ServerSentEvent event = new ServerSentEventImpl("theName", THREE_LINES_DATA, null, null);
    encoder.writeTo(writer, event);
    assertThat(writer.toString(), is("""
        event: theName
        data: line1
        data: line2
        data: line3

        """));
  }

  @Test
  public void threeLinesWithAndId() throws Exception {
    ServerSentEvent event = new ServerSentEventImpl("theName", THREE_LINES_DATA, "theId", null);
    encoder.writeTo(writer, event);
    assertThat(writer.toString(), is("""
        event: theName
        data: line1
        data: line2
        data: line3
        id: theId

        """));
  }

  @Test
  public void threeLinesWithAndRetry() throws Exception {
    ServerSentEvent event = new ServerSentEventImpl("theName", THREE_LINES_DATA, null, 3000L);
    encoder.writeTo(writer, event);
    assertThat(writer.toString(), is("""
        event: theName
        data: line1
        data: line2
        data: line3
        retry: 3000

        """));
  }

  @Test
  public void threeLinesIdAndRetry() throws Exception {
    ServerSentEvent event = new ServerSentEventImpl("theName", THREE_LINES_DATA, "theId", 3000L);
    encoder.writeTo(writer, event);
    assertThat(writer.toString(), is("""
        event: theName
        data: line1
        data: line2
        data: line3
        id: theId
        retry: 3000

        """));
  }

  @Test
  public void emptyData() throws IOException {
    ServerSentEvent event = new ServerSentEventImpl("theName", "", null, null);
    encoder.writeTo(writer, event);
    assertThat(writer.toString(), is("event: theName\ndata: \n\n"));
  }

  @Test
  public void precondition_eventNameCantBeNull() {
    NullPointerException exception =
        assertThrows(NullPointerException.class, () -> new ServerSentEventImpl(null, "", null, null));
    assertThat(exception, hasMessage(is("eventName cannot be null")));
  }

  @Test
  public void precondition_dataCantBeNull() {
    NullPointerException exception =
        assertThrows(NullPointerException.class, () -> new ServerSentEventImpl("theName", null, null, null));
    assertThat(exception, hasMessage(is("eventData cannot be null")));
  }

}
