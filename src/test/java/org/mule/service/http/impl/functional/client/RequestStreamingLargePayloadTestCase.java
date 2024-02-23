/*
 * Copyright 2023 Salesforce, Inc. All rights reserved.
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.service.http.impl.functional.client;

import static org.mule.runtime.core.api.util.ClassUtils.getClassPathRoot;
import static org.mule.runtime.http.api.HttpConstants.HttpStatus.OK;
import static org.mule.runtime.http.api.HttpConstants.Method.POST;
import static org.mule.service.http.impl.AllureConstants.HttpFeature.HttpStory.STREAMING;

import static java.net.URI.create;
import static java.nio.file.Files.newInputStream;
import static java.nio.file.Paths.get;

import org.mule.runtime.http.api.client.HttpRequestOptions;
import org.mule.runtime.http.api.domain.entity.InputStreamHttpEntity;
import org.mule.runtime.http.api.domain.message.request.HttpRequest;
import org.mule.runtime.http.api.domain.message.response.HttpResponse;
import org.mule.runtime.http.api.domain.message.response.HttpResponseBuilder;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

import io.qameta.allure.Issue;
import io.qameta.allure.Story;
import io.qameta.allure.junit4.DisplayName;
import org.apache.commons.io.FileUtils;
import org.junit.After;
import org.junit.Before;

@Story(STREAMING)
@DisplayName("Validates request streaming with a large payload")
@Issue("W-14543363")
public class RequestStreamingLargePayloadTestCase extends HttpClientPostStreamingTestCase {


  @Before
  public void before() throws Exception {
    setRequestStreaming(true);
  }

  public RequestStreamingLargePayloadTestCase(String serviceToLoad) {
    super(serviceToLoad);
  }

  @After
  public void after() throws Exception {
    setRequestStreaming(false);
  }

  protected InputStream getInputStream() {
    try {
      return newInputStream(get(create(getClassPathRoot(RequestStreamingLargePayloadTestCase.class).toURI()
          + "largePayload")));
    } catch (Exception e) {
      throw new AssertionError("Error on loading the large payload file");
    }
  }

  @Override
  public HttpRequest getRequest() {
    return HttpRequest.builder().method(POST).uri(getUri())
        .entity(new InputStreamHttpEntity(getInputStream())).build();
  }

  @Override
  public HttpRequestOptions getOptions() {
    return HttpRequestOptions.builder().responseTimeout(RESPONSE_TIMEOUT).build();
  }

  @Override
  public HttpResponse doSetUpHttpResponse(HttpRequest request) {
    HttpResponseBuilder response = HttpResponse.builder();
    extractPayload(request);
    response.statusCode(OK.getStatusCode());
    return response.build();
  }

  @Override
  protected String expectedPayload() {
    try {
      return FileUtils
          .readFileToString(new File(getClassPathRoot(RequestStreamingLargePayloadTestCase.class).getPath()
              + "largePayload"));
    } catch (IOException e) {
      throw new AssertionError("Error on loading the large payload file");
    }
  }
}
