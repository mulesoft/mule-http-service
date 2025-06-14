/*
 * Copyright 2023 Salesforce, Inc. All rights reserved.
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.service.http.impl.functional.client;

import static org.mule.runtime.http.api.HttpConstants.HttpStatus.OK;
import static org.mule.runtime.http.api.HttpConstants.HttpStatus.TEMPORARY_REDIRECT;
import static org.mule.runtime.http.api.HttpConstants.Method.POST;
import static org.mule.service.http.impl.AllureConstants.HttpFeature.HttpStory.STREAMING;

import org.mule.runtime.http.api.client.HttpRequestOptions;
import org.mule.runtime.http.api.domain.entity.InputStreamHttpEntity;
import org.mule.runtime.http.api.domain.message.request.HttpRequest;
import org.mule.runtime.http.api.domain.message.response.HttpResponse;
import org.mule.runtime.http.api.domain.message.response.HttpResponseBuilder;

import java.io.ByteArrayInputStream;

import org.junit.jupiter.api.DisplayName;

import io.qameta.allure.Story;

@Story(STREAMING)
@DisplayName("Validates that the POST body is preserved on redirect")
public class RedirectHttpClientPostStreamingTestCase extends HttpClientPostStreamingTestCase {

  public RedirectHttpClientPostStreamingTestCase(String serviceToLoad) {
    super(serviceToLoad);
  }

  @Override
  public HttpResponse setUpHttpResponse(HttpRequest request) {
    HttpResponseBuilder response = HttpResponse.builder();
    if (request.getUri().getPath().equals("/first")) {
      return response.statusCode(TEMPORARY_REDIRECT.getStatusCode()).addHeader("Location", "/bla").build();
    } else {
      extractPayload(request);
      return response.statusCode(OK.getStatusCode()).build();
    }
  }

  @Override
  public HttpResponse doSetUpHttpResponse(HttpRequest request) {
    HttpResponseBuilder response = HttpResponse.builder();
    if (request.getUri().getPath().equals("/first")) {
      return response.statusCode(TEMPORARY_REDIRECT.getStatusCode()).addHeader("Location", "/bla").build();
    } else {
      extractPayload(request);
      return response.statusCode(OK.getStatusCode()).build();
    }
  }

  @Override
  public HttpRequestOptions getOptions() {
    return HttpRequestOptions.builder().responseTimeout(RESPONSE_TIMEOUT).build();
  }

  @Override
  public HttpRequest getRequest() {
    return HttpRequest.builder().method(POST).uri(getUri() + "/first")
        .entity(new InputStreamHttpEntity(new ByteArrayInputStream(TEST_PAYLOAD.getBytes()))).build();
  }

}
