/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.service.http.impl.functional.server;

import static java.util.Collections.singletonList;
import static org.apache.commons.lang3.StringUtils.countMatches;
import static org.apache.http.entity.ContentType.TEXT_PLAIN;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;
import static org.mule.runtime.http.api.HttpConstants.HttpStatus.INTERNAL_SERVER_ERROR;
import static org.mule.runtime.http.api.HttpHeaders.Names.CONTENT_TYPE;
import static org.mule.runtime.http.api.HttpHeaders.Values.MULTIPART_FORM_DATA;
import static org.mule.service.http.impl.AllureConstants.HttpFeature.HttpStory.MULTIPART;

import org.mule.runtime.core.api.util.IOUtils;
import org.mule.runtime.http.api.domain.entity.multipart.HttpPart;
import org.mule.runtime.http.api.domain.entity.multipart.MultipartHttpEntity;
import org.mule.runtime.http.api.domain.message.response.HttpResponse;
import org.mule.runtime.http.api.server.HttpServer;
import org.mule.runtime.http.api.server.HttpServerConfiguration;
import org.mule.service.http.impl.functional.AbstractHttpServiceTestCase;
import org.mule.tck.junit4.rule.DynamicPort;

import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.io.IOException;
import java.util.Collection;

import io.qameta.allure.Story;

@Story(MULTIPART)
public class HttpServerPartsTestCase extends AbstractHttpServiceTestCase {

  private static final String TEXT_BODY_FIELD_NAME = "field1";
  private static final String TEXT_BODY_FIELD_VALUE = "yes";
  private static final String BASE_PATH = "/";
  private static final String NO_HEADER = "/no-header";
  private static final String PARTIAL_HEADER = "/partial-header";
  private static final String FULL_HEADER = "/full-header";
  private static final String BOUNDARY_PART = "; boundary=\"the-boundary\"";

  @Rule
  public DynamicPort port = new DynamicPort("port");

  private HttpServer server;

  public HttpServerPartsTestCase(String serviceToLoad) {
    super(serviceToLoad);
  }

  @Before
  public void setUp() throws Exception {
    server = service.getServerFactory().create(new HttpServerConfiguration.Builder()
        .setHost("localhost")
        .setPort(port.getNumber())
        .setName("parts-test")
        .build());
    server.start();
    server.addRequestHandler(BASE_PATH, (requestContext, responseCallback) -> {
      IgnoreResponseStatusCallback statusCallback = new IgnoreResponseStatusCallback();
      try {
        Collection<HttpPart> parts = requestContext.getRequest().getEntity().getParts();
        responseCallback.responseReady(HttpResponse.builder().entity(new MultipartHttpEntity(parts)).build(), statusCallback);
      } catch (IOException e) {
        responseCallback.responseReady(HttpResponse.builder().statusCode(INTERNAL_SERVER_ERROR.getStatusCode()).build(),
                                       statusCallback);
      }
    });
    server.addRequestHandler(NO_HEADER, (requestContext, responseCallback) -> {
      responseCallback.responseReady(HttpResponse.builder().entity(new MultipartHttpEntity(createPart())).build(),
                                     new IgnoreResponseStatusCallback());
    });
    server.addRequestHandler(PARTIAL_HEADER, (requestContext, responseCallback) -> {
      responseCallback.responseReady(HttpResponse.builder().entity(new MultipartHttpEntity(createPart()))
          .addHeader(CONTENT_TYPE, MULTIPART_FORM_DATA).build(), new IgnoreResponseStatusCallback());
    });
    server.addRequestHandler(FULL_HEADER, (requestContext, responseCallback) -> {
      responseCallback.responseReady(HttpResponse.builder().entity(new MultipartHttpEntity(createPart()))
          .addHeader(CONTENT_TYPE, MULTIPART_FORM_DATA + BOUNDARY_PART).build(), new IgnoreResponseStatusCallback());
    });
  }

  @After
  public void tearDown() {
    if (server != null) {
      server.stop();
      server.dispose();
    }
  }

  @Test
  public void returnsOnlyOneContentTypeHeaderPerPart() throws Exception {
    try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
      HttpPost httpPost = new HttpPost(getUri("/"));
      httpPost.setEntity(getMultipartEntity());
      try (CloseableHttpResponse response = httpClient.execute(httpPost)) {
        assertThat(countMatches(IOUtils.toString(response.getEntity().getContent()), CONTENT_TYPE), is(1));
      }
    }
  }

  @Test
  public void maintainsBoundaryWhenPresent() throws Exception {
    assertBoundaryMatch(FULL_HEADER, BOUNDARY_PART);
  }

  @Test
  public void addsBoundaryWhenNoHeaderIsPresent() throws Exception {
    assertBoundaryMatch(NO_HEADER, "; boundary=\"");
  }

  @Test
  public void addsBoundaryWhenNotPresent() throws Exception {
    assertBoundaryMatch(PARTIAL_HEADER, "; boundary=\"");
  }

  private void assertBoundaryMatch(String path, String boundaryPart) throws IOException {
    try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
      HttpPost httpPost = new HttpPost(getUri(path));
      httpPost.setEntity(getMultipartEntity());
      try (CloseableHttpResponse response = httpClient.execute(httpPost)) {
        String contentType = response.getFirstHeader(CONTENT_TYPE).getValue();
        assertThat(contentType, startsWith(MULTIPART_FORM_DATA));
        assertThat(contentType, containsString(boundaryPart));
      }
    }
  }

  private String getUri(String path) {
    return String.format("http://localhost:%s%s", port.getValue(), path);
  }

  private Collection<HttpPart> createPart() {
    HttpPart part = new HttpPart(TEXT_BODY_FIELD_NAME, TEXT_BODY_FIELD_VALUE.getBytes(), "text/plain",
                                 TEXT_BODY_FIELD_VALUE.length());
    return singletonList(part);
  }

  private HttpEntity getMultipartEntity() {
    MultipartEntityBuilder builder = MultipartEntityBuilder.create();
    builder.addTextBody(TEXT_BODY_FIELD_NAME, TEXT_BODY_FIELD_VALUE, TEXT_PLAIN);
    return builder.build();
  }

}
