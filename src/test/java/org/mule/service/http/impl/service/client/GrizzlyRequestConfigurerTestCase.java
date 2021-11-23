/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.service.http.impl.service.client;

import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.junit.MockitoJUnit.rule;
import static org.mule.runtime.api.util.MultiMap.emptyMultiMap;

import com.ning.http.client.Request;
import com.ning.http.client.RequestBuilder;
import com.ning.http.client.providers.grizzly.FeedableBodyGenerator;
import io.qameta.allure.Issue;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoRule;
import org.mule.runtime.http.api.client.HttpRequestOptions;
import org.mule.runtime.http.api.domain.entity.HttpEntity;
import org.mule.runtime.http.api.domain.message.request.HttpRequest;
import org.mule.service.http.impl.service.client.GrizzlyHttpClient.RequestConfigurer;

import java.io.IOException;

public class GrizzlyRequestConfigurerTestCase {

  @Rule
  public MockitoRule mockitorule = rule();

  @Mock
  private GrizzlyHttpClient client;

  @Mock
  private HttpRequestOptions options;

  @Test
  @Issue("MULE-19424")
  public void theGrizzlyRequestIsConfiguredWithAFeedableBodyGeneratorWhenStreamingIsEnable() throws IOException {
    // Given that the request streaming flag is enabled
    boolean requestStreamingEnabled = true;

    // When we configure the grizzly request
    Request grizzlyRequest = configureGrizzlyRequest(requestStreamingEnabled);

    // Then the grizzly request body generator is feedable
    assertThat(grizzlyRequest.getBodyGenerator(), instanceOf(FeedableBodyGenerator.class));
  }

  @Test
  @Issue("MULE-19424")
  public void theGrizzlyRequestIsNotConfiguredWithAFeedableBodyGeneratorWhenStreamingIsDisabled() throws IOException {
    // Given that the request streaming flag is disabled
    boolean isRequestStreamingEnabled = false;

    // When we configure the grizzly request
    Request grizzlyRequest = configureGrizzlyRequest(isRequestStreamingEnabled);

    // Then the grizzly request body generator is not feedable
    assertThat(grizzlyRequest.getBodyGenerator(), not(instanceOf(FeedableBodyGenerator.class)));
  }

  private HttpRequest mockMuleRequestWithStreamingEntity() {
    HttpRequest muleRequest = mock(HttpRequest.class);
    HttpEntity requestEntity = mock(HttpEntity.class);
    when(requestEntity.isStreaming()).thenReturn(true);
    when(muleRequest.getMethod()).thenReturn("POST");
    when(muleRequest.getQueryParams()).thenReturn(emptyMultiMap());
    when(muleRequest.getEntity()).thenReturn(requestEntity);
    return muleRequest;
  }

  private Request configureGrizzlyRequest(boolean isRequestStreamingEnabled) throws IOException {
    HttpRequest muleRequest = mockMuleRequestWithStreamingEntity();
    final RequestBuilder requestBuilder = new RequestBuilder(muleRequest.getMethod(), true);
    RequestConfigurer configurer =
        new GrizzlyRequestConfigurer(client, options, muleRequest, false, isRequestStreamingEnabled, 8 << 10);
    configurer.configure(requestBuilder);
    return requestBuilder.build();
  }
}
