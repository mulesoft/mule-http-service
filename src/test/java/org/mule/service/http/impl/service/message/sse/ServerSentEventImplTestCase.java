/*
 * Copyright 2023 Salesforce, Inc. All rights reserved.
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.service.http.impl.service.message.sse;

import static org.mule.service.http.impl.AllureConstants.HttpFeature.HTTP_SERVICE;
import static org.mule.service.http.impl.AllureConstants.HttpFeature.HttpStory.SSE;
import static org.mule.service.http.impl.AllureConstants.HttpFeature.HttpStory.SSE_PROTOCOL;
import static org.mule.service.http.impl.tck.IsOptionalOf.isOptionalOf;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasToString;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThrows;
import static org.junit.internal.matchers.ThrowableMessageMatcher.hasMessage;

import org.mule.tck.junit4.AbstractMuleTestCase;

import io.qameta.allure.Feature;
import io.qameta.allure.Story;
import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.Test;

@Feature(HTTP_SERVICE)
@Story(SSE)
@Story(SSE_PROTOCOL)
public class ServerSentEventImplTestCase extends AbstractMuleTestCase {

  @Test
  public void hashAndEquals() {
    EqualsVerifier.simple().forClass(ServerSentEventImpl.class).verify();
  }

  @Test
  public void requiredParametersCannotBeNull() {
    var dataCantBeNull = assertThrows(NullPointerException.class, () -> new ServerSentEventImpl("name", null, null, null));
    assertThat(dataCantBeNull, hasMessage(is("eventData cannot be null")));

    var nameCantBeNull = assertThrows(NullPointerException.class, () -> new ServerSentEventImpl(null, "data", null, null));
    assertThat(nameCantBeNull, hasMessage(is("eventName cannot be null")));
  }

  @Test
  public void toStringShowsAllFields() {
    var eventFull = new ServerSentEventImpl("name", "data1\ndata2", "id", 500L);
    assertThat(eventFull, hasToString("ServerSentEvent [name=name, data=data1\ndata2, id=id, retryDelay=500]"));
  }

  @Test
  public void toStringShowsAbsentFieldsAsNulls() {
    var eventFull = new ServerSentEventImpl("name", "data1\ndata2", null, null);
    assertThat(eventFull, hasToString("ServerSentEvent [name=name, data=data1\ndata2, id=null, retryDelay=null]"));
  }

  @Test
  public void getters() {
    var eventFull = new ServerSentEventImpl("name", "data1\ndata2", "id", 500L);
    assertThat(eventFull.getName(), is("name"));
    assertThat(eventFull.getData(), is("data1\ndata2"));
    assertThat(eventFull.getId(), is(isOptionalOf("id")));
    assertThat(eventFull.getRetryDelay(), is(isOptionalOf(500L)));
  }
}
