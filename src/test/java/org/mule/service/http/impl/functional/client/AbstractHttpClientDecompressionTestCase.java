/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.service.http.impl.functional.client;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.mule.runtime.http.api.HttpHeaders.Names.ACCEPT_ENCODING;
import static org.mule.runtime.http.api.HttpHeaders.Names.CONTENT_ENCODING;
import static org.mule.runtime.http.api.HttpHeaders.Values.GZIP;
import org.mule.runtime.http.api.client.HttpClient;
import org.mule.runtime.http.api.client.HttpClientConfiguration;
import org.mule.runtime.http.api.domain.entity.ByteArrayHttpEntity;
import org.mule.runtime.http.api.domain.message.request.HttpRequest;
import org.mule.runtime.http.api.domain.message.request.HttpRequestBuilder;
import org.mule.runtime.http.api.domain.message.response.HttpResponse;
import org.mule.runtime.http.api.domain.message.response.HttpResponseBuilder;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.concurrent.TimeoutException;
import java.util.zip.GZIPOutputStream;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public abstract class AbstractHttpClientDecompressionTestCase extends AbstractHttpClientTestCase {

  public static final String TEST_MESSAGE = "This is a regular message.";

  private HttpClient client;
  private byte[] compressedData;

  public AbstractHttpClientDecompressionTestCase(String serviceToLoad) {
    super(serviceToLoad);
  }

  @Before
  public void createClient() throws IOException {
    client = service.getClientFactory().create(getClientConfiguration());
    client.start();
    compressedData = getCompressedData().toByteArray();
  }

  protected abstract HttpClientConfiguration getClientConfiguration();

  @After
  public void stopClient() {
    if (client != null) {
      client.stop();
    }
  }

  @Override
  protected HttpResponse setUpHttpResponse(HttpRequest request) {
    HttpResponseBuilder builder = HttpResponse.builder().entity(new ByteArrayHttpEntity(compressedData));
    if (request.getHeaderValue(ACCEPT_ENCODING) != null) {
      builder.addHeader(CONTENT_ENCODING, GZIP);
    }
    return builder.build();
  }

  private ByteArrayOutputStream getCompressedData() throws IOException {
    ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
    GZIPOutputStream gzipOutputStream = new GZIPOutputStream(byteArrayOutputStream);
    gzipOutputStream.write(TEST_MESSAGE.getBytes());
    gzipOutputStream.flush();
    gzipOutputStream.close();
    return byteArrayOutputStream;
  }

  @Test
  public void decompressesResponseWhenIndicated() throws IOException, TimeoutException {
    validateResponse(HttpRequest.builder().addHeader(ACCEPT_ENCODING, GZIP), TEST_MESSAGE.getBytes());
  }

  @Test
  public void doesNotDecompressDataWithoutIndication() throws IOException, TimeoutException {
    validateResponse(HttpRequest.builder(), compressedData);
  }

  private void validateResponse(HttpRequestBuilder builder, byte[] expectedResponse) throws IOException, TimeoutException {
    HttpResponse response = client.send(builder.uri(getUri()).build());

    assertThat(response.getEntity().getBytes(), is(expectedResponse));
  }
}
