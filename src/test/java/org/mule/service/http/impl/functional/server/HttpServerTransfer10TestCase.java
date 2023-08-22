/*
 * Copyright 2023 Salesforce, Inc. All rights reserved.
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.service.http.impl.functional.server;

import static org.apache.http.HttpVersion.HTTP_1_0;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertThat;
import static org.mule.runtime.core.api.util.StringUtils.EMPTY;
import static org.mule.runtime.http.api.HttpHeaders.Names.CONNECTION;
import static org.mule.runtime.http.api.HttpHeaders.Names.CONTENT_LENGTH;
import static org.mule.runtime.http.api.HttpHeaders.Values.CLOSE;

import org.mule.runtime.core.api.util.IOUtils;

import java.io.IOException;

import org.apache.http.HttpVersion;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.hamcrest.Matcher;
import org.junit.Test;

public class HttpServerTransfer10TestCase extends HttpServerTransferTestCase {

  public HttpServerTransfer10TestCase(String serviceToLoad) {
    super(serviceToLoad);
  }

  @Override
  public HttpVersion getVersion() {
    return HTTP_1_0;
  }

  @Test
  public void defaultsStreamingWhenEmpty() throws Exception {
    verify10Headers(EMPTY, CLOSE, is(nullValue()), EMPTY);
  }

  @Test
  public void defaultsStreamingWhenBytes() throws Exception {
    verify10Headers(BYTES, CLOSE, is(nullValue()), DATA);
  }

  @Test
  public void defaultsStreamingWhenMultipart() throws Exception {
    verify10Headers(MULTIPART, CLOSE, is(nullValue()), MULTIPART_DATA);
  }

  @Test
  public void defaultsStreamingWhenStream() throws Exception {
    verify10Headers(STREAM, CLOSE, is(nullValue()), DATA);
  }

  private void verify10Headers(String path, String expectedConnection, Matcher<Object> contentLengthMatcher,
                               String expectedBody)
      throws IOException {
    try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
      HttpGet httpGet = new HttpGet(getUri(path));
      httpGet.setProtocolVersion(getVersion());
      try (CloseableHttpResponse response = httpClient.execute(httpGet)) {
        assertThat(getHeaderValue(response, CONNECTION), is(expectedConnection));
        assertThat(getHeaderValue(response, CONTENT_LENGTH), contentLengthMatcher);
        assertThat(IOUtils.toString(response.getEntity().getContent()), is(expectedBody));
      }
    }
  }

}
