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

import com.sun.mail.util.LineInputStream;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.Collection;
import java.util.Optional;

import io.qameta.allure.Story;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.junit.Before;
import org.junit.Test;

@Story(MULTIPART)
public class HttpServerPartsTestCase extends AbstractHttpServerTestCase {

  private static final String MIME_UTF_PROPERTY = "mail.mime.allowutf8";
  private static final String MIME_PROPERTY_READ_FIELD_NAME = "defaultutf8";

  private static final String TEXT_BODY_FIELD_NAME = "field1";
  private static final String TEXT_BODY_FIELD_VALUE = "yes";
  private static final String BASE_PATH = "/";
  private static final String NO_HEADER = "/no-header";
  private static final String PARTIAL_HEADER = "/partial-header";
  private static final String FULL_HEADER = "/full-header";
  private static final String UTF = "/utf";
  private static final String BOUNDARY_PART = "; boundary=\"the-boundary\"";
  private static final String MIXED_CONTENT =
      "--the-boundary\r\n"
          + "Content-Type: text/plain; charset=ISO-8859-1\r\n"
          + "Content-Transfer-Encoding: 8bit\r\n"
          + "Content-Disposition: inline; name=\"field1\"; filename=\"£10.txt\" \r\n"
          + "\r\n"
          + "yes\r\n"
          + "--the-boundary--\r\n";

  public HttpServerPartsTestCase(String serviceToLoad) {
    super(serviceToLoad);
  }

  @Rule
  public SystemProperty encoding = new SystemProperty(MIME_UTF_PROPERTY, "true");

  @Before
  public void setUp() throws Exception {
    setUpServer();
    IgnoreResponseStatusCallback statusCallback = new IgnoreResponseStatusCallback();
    server.addRequestHandler(BASE_PATH, (requestContext, responseCallback) -> {
      try {
        Collection<HttpPart> parts = requestContext.getRequest().getEntity().getParts();
        responseCallback.responseReady(HttpResponse.builder().entity(new MultipartHttpEntity(parts)).build(), statusCallback);
      } catch (IOException e) {
        responseCallback.responseReady(HttpResponse.builder().statusCode(INTERNAL_SERVER_ERROR.getStatusCode()).build(),
                                       statusCallback);
      }
    });
    server.addRequestHandler(UTF, (requestContext, responseCallback) -> {
      try {
        Collection<HttpPart> parts = requestContext.getRequest().getEntity().getParts();
        responseCallback.responseReady(HttpResponse.builder().entity(new MultipartHttpEntity(parts))
            .addHeader(CONTENT_TYPE, requestContext.getRequest().getHeaderValue(CONTENT_TYPE))
            .build(), statusCallback);
      } catch (IOException e) {
        responseCallback.responseReady(HttpResponse.builder().statusCode(INTERNAL_SERVER_ERROR.getStatusCode()).build(),
                                       statusCallback);
      }
    });
    server.addRequestHandler(NO_HEADER, (requestContext, responseCallback) -> {
      responseCallback.responseReady(HttpResponse.builder().entity(new MultipartHttpEntity(createPart())).build(),
                                     statusCallback);
    });
    server.addRequestHandler(PARTIAL_HEADER, (requestContext, responseCallback) -> {
      responseCallback.responseReady(HttpResponse.builder().entity(new MultipartHttpEntity(createPart()))
          .addHeader(CONTENT_TYPE, MULTIPART_FORM_DATA).build(), statusCallback);
    });
    server.addRequestHandler(FULL_HEADER, (requestContext, responseCallback) -> {
      responseCallback.responseReady(HttpResponse.builder().entity(new MultipartHttpEntity(createPart()))
          .addHeader(CONTENT_TYPE, MULTIPART_FORM_DATA + BOUNDARY_PART).build(), statusCallback);
    });
  }

  @Override
  protected String getServerName() {
    return "parts-test";
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
  public void utf8InHeaders() throws Exception {
    // The system property is read to a static field, so it will be set before this test executes
    Optional<Field> utf8Property = getMailProperty();
    Boolean previousValue = null;
    if (utf8Property.isPresent()) {
      utf8Property.get().setAccessible(true);
      previousValue = utf8Property.get().getBoolean(null);
    }

    try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
      if (utf8Property.isPresent()) {
        // Mimic the behavior of the property having been present at load time
        utf8Property.get().setBoolean(null, getBoolean(MIME_UTF_PROPERTY));
      }
      HttpPost httpPost = new HttpPost(getUri(UTF));
      httpPost.setEntity(new ByteArrayEntity(MIXED_CONTENT.getBytes(), ContentType.create(
                                                                                          "multipart/mixed", ISO_8859_1)
          .withParameters(new BasicNameValuePair("boundary", "the-boundary"))));
      try (CloseableHttpResponse response = httpClient.execute(httpPost)) {
        assertThat(IOUtils.toString(response.getEntity().getContent()), is(MIXED_CONTENT));
      }
    } finally {
      if (previousValue != null) {
        utf8Property.get().setBoolean(null, previousValue);
      }
    }
  }

  private Optional<Field> getMailProperty() {
    try {
      return of(LineInputStream.class.getDeclaredField(MIME_PROPERTY_READ_FIELD_NAME));
    } catch (NoSuchFieldException e) {
      return empty();
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
