/*
 * Copyright 2023 Salesforce, Inc. All rights reserved.
 */
package org.mule.service.http.impl.functional.server;

import static java.util.Collections.singletonList;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertThat;
import static org.mule.runtime.core.api.util.StringUtils.EMPTY;
import static org.mule.runtime.http.api.HttpHeaders.Names.CONTENT_LENGTH;
import static org.mule.runtime.http.api.HttpHeaders.Names.CONTENT_TYPE;
import static org.mule.runtime.http.api.HttpHeaders.Names.TRANSFER_ENCODING;
import static org.mule.runtime.http.api.HttpHeaders.Values.CHUNKED;
import static org.mule.runtime.http.api.HttpHeaders.Values.MULTIPART_FORM_DATA;
import static org.mule.service.http.impl.AllureConstants.HttpFeature.HttpStory.TRANSFER_TYPE;
import org.mule.runtime.api.util.Pair;
import org.mule.runtime.core.api.util.IOUtils;
import org.mule.runtime.http.api.domain.entity.ByteArrayHttpEntity;
import org.mule.runtime.http.api.domain.entity.HttpEntity;
import org.mule.runtime.http.api.domain.entity.InputStreamHttpEntity;
import org.mule.runtime.http.api.domain.entity.multipart.HttpPart;
import org.mule.runtime.http.api.domain.entity.multipart.MultipartHttpEntity;
import org.mule.runtime.http.api.domain.message.response.HttpResponse;
import org.mule.runtime.http.api.domain.message.response.HttpResponseBuilder;
import org.mule.runtime.http.api.server.async.ResponseStatusCallback;

import java.io.ByteArrayInputStream;
import java.io.IOException;

import io.qameta.allure.Story;
import org.apache.http.Header;
import org.apache.http.HttpVersion;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.hamcrest.Matcher;
import org.junit.Before;
import org.junit.Test;

@Story(TRANSFER_TYPE)
public abstract class HttpServerTransferTestCase extends AbstractHttpServerTestCase {

  protected static final String DATA = "My awesome data";
  protected static final String MULTIPART_DATA = "--bounds\r\n"
      + "Content-Type: text/plain\r\n"
      + "Content-Disposition: form-data; name=\"name\"\r\n"
      + "\r\n"
      + DATA + "\r\n"
      + "--bounds--\r\n";
  protected static final byte[] DATA_BYTES = DATA.getBytes();
  protected static final String DATA_SIZE = String.valueOf(DATA_BYTES.length);
  protected static final String MULTIPART_SIZE = "112";
  protected static final Pair<String, String> CHUNKED_PAIR = new Pair<>(TRANSFER_ENCODING, CHUNKED);
  protected static final String STREAM = "/stream";
  protected static final String BYTES = "/bytes";
  protected static final String MULTIPART = "/multipart";

  protected Pair<String, String> headerToSend;

  public HttpServerTransferTestCase(String serviceToLoad) {
    super(serviceToLoad);
  }

  @Before
  public void setUp() throws Exception {
    setUpServer();
    ResponseStatusCallback statusCallback = new IgnoreResponseStatusCallback();

    server.addRequestHandler("/", (requestContext, responseCallback) -> {
      responseCallback.responseReady(getResponse().build(), statusCallback);
    });
    server.addRequestHandler(STREAM, (requestContext, responseCallback) -> {
      HttpEntity entity = new InputStreamHttpEntity(new ByteArrayInputStream(DATA_BYTES));
      responseCallback.responseReady(getResponse().entity(entity).build(), statusCallback);
    });
    server.addRequestHandler(BYTES, (requestContext, responseCallback) -> {
      HttpEntity entity = new ByteArrayHttpEntity(DATA_BYTES);
      responseCallback.responseReady(getResponse().entity(entity).build(), statusCallback);
    });
    server.addRequestHandler(MULTIPART, (requestContext, responseCallback) -> {
      HttpPart part = new HttpPart("name", DATA_BYTES, "text/plain", DATA_BYTES.length);
      HttpEntity entity = new MultipartHttpEntity(singletonList(part));
      responseCallback.responseReady(getResponse()
          .entity(entity)
          .addHeader(CONTENT_TYPE, MULTIPART_FORM_DATA + "; boundary=\"bounds\"")
          .build(), statusCallback);
    });
  }

  @Override
  protected String getServerName() {
    return "transfer-test";
  }

  private HttpResponseBuilder getResponse() {
    HttpResponseBuilder responseBuilder = HttpResponse.builder();
    if (headerToSend != null) {
      responseBuilder.addHeader(headerToSend.getFirst(), headerToSend.getSecond());
    }
    return responseBuilder;
  }

  public abstract HttpVersion getVersion();

  @Test
  public void usesLengthWhenEmptyAndHeader() throws Exception {
    headerToSend = new Pair<>(CONTENT_LENGTH, "0");
    verifyTransferHeaders(EMPTY, is(nullValue()), is("0"), EMPTY);
  }

  @Test
  public void usesLengthWhenBytesAndHeader() throws Exception {
    headerToSend = new Pair<>(CONTENT_LENGTH, DATA_SIZE);
    verifyTransferHeaders(BYTES, is(nullValue()), is(DATA_SIZE), DATA);
  }

  @Test
  public void usesLengthWhenMultipartAndHeader() throws Exception {
    headerToSend = new Pair<>(CONTENT_LENGTH, MULTIPART_SIZE);
    verifyTransferHeaders(MULTIPART, is(nullValue()), is(MULTIPART_SIZE), MULTIPART_DATA);
  }

  @Test
  public void usesLengthWhenStreamAndHeader() throws Exception {
    headerToSend = new Pair<>(CONTENT_LENGTH, DATA_SIZE);
    verifyTransferHeaders(STREAM, is(nullValue()), is(DATA_SIZE), DATA);
  }

  protected void verifyTransferHeaders(String path, Matcher<Object> transferEncodingMatcher, Matcher<Object> contentLengthMatcher,
                                       String expectedBody)
      throws IOException {
    try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
      HttpGet httpGet = new HttpGet(getUri(path));
      httpGet.setProtocolVersion(getVersion());
      try (CloseableHttpResponse response = httpClient.execute(httpGet)) {
        assertThat(getHeaderValue(response, TRANSFER_ENCODING), transferEncodingMatcher);
        assertThat(getHeaderValue(response, CONTENT_LENGTH), contentLengthMatcher);
        assertThat(IOUtils.toString(response.getEntity().getContent()), is(expectedBody));
      }
    }
  }

  protected String getHeaderValue(CloseableHttpResponse response, String name) {
    Header header = response.getFirstHeader(name);
    return header != null ? header.getValue() : null;
  }

  protected String getUri(String path) {
    return "http://localhost:" + port.getValue() + path;
  }

}
