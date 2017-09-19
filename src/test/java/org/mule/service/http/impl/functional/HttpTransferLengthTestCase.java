/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.service.http.impl.functional;

import static java.lang.Long.valueOf;
import static java.util.Collections.singletonList;
import static java.util.Optional.of;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.mule.runtime.http.api.HttpConstants.HttpStatus.OK;
import static org.mule.runtime.http.api.HttpHeaders.Names.CONTENT_TYPE;
import static org.mule.service.http.impl.AllureConstants.HttpFeature.HttpStory.TRANSFER_TYPE;
import static org.mule.tck.junit4.matcher.IsEmptyOptional.empty;
import org.mule.runtime.http.api.client.HttpClient;
import org.mule.runtime.http.api.client.HttpClientConfiguration;
import org.mule.runtime.http.api.domain.entity.ByteArrayHttpEntity;
import org.mule.runtime.http.api.domain.entity.InputStreamHttpEntity;
import org.mule.runtime.http.api.domain.entity.multipart.HttpPart;
import org.mule.runtime.http.api.domain.entity.multipart.MultipartHttpEntity;
import org.mule.runtime.http.api.domain.message.request.HttpRequest;
import org.mule.runtime.http.api.domain.message.response.HttpResponse;
import org.mule.runtime.http.api.domain.message.response.HttpResponseBuilder;
import org.mule.service.http.impl.functional.client.AbstractHttpClientTestCase;

import java.io.ByteArrayInputStream;
import java.util.Optional;

import io.qameta.allure.Story;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

@Story(TRANSFER_TYPE)
public class HttpTransferLengthTestCase extends AbstractHttpClientTestCase {

  private static final String RESPONSE = "TEST";
  private static final String REQUEST = "tests";
  public static final String BYTE = "/byte";
  public static final String MULTIPART = "/multipart";
  public static final String STREAM = "/stream";
  public static final String CHUNKED = "/chunked";

  private HttpClient client;

  public HttpTransferLengthTestCase(String serviceToLoad) {
    super(serviceToLoad);
  }

  @Before
  public void createClient() {
    HttpClientConfiguration clientConf = new HttpClientConfiguration.Builder().setName("multipart-test").build();
    client = service.getClientFactory().create(clientConf);
    client.start();
  }

  @After
  public void closeClient() {
    if (client != null) {
      client.stop();
    }
  }

  @Override
  protected HttpResponse setUpHttpResponse(HttpRequest request) {
    String path = request.getPath();
    HttpResponseBuilder builder = HttpResponse.builder();
    try {
      Optional<Long> expectedRequestLength = of(valueOf(REQUEST.length()));
      if (BYTE.equals(path)) {
        builder.entity(new ByteArrayHttpEntity(RESPONSE.getBytes()));
      } else if (MULTIPART.equals(path)) {
        expectedRequestLength = of(142L);
        HttpPart part = new HttpPart("part1", "TEST".getBytes(), "text/plain", 4);
        builder
            .entity(new MultipartHttpEntity(singletonList(part)))
            .addHeader(CONTENT_TYPE, "multipart/form-data; boundary=\"bounds\"");
      } else if (STREAM.equals(path)) {
        builder.entity(new InputStreamHttpEntity(new ByteArrayInputStream("TEST".getBytes()), 4L));
      } else if (CHUNKED.equals(path)) {
        expectedRequestLength = Optional.empty();
        builder.entity(new InputStreamHttpEntity(new ByteArrayInputStream("TEST".getBytes())));
      } else {
        expectedRequestLength = of(0L);
      }
      assertThat(request.getEntity().getLength(), is(expectedRequestLength));
      return builder.build();
    } catch (AssertionError e) {
      return builder.statusCode(500).entity(new ByteArrayHttpEntity(e.getMessage().getBytes())).build();
    }
  }

  @Test
  public void propagatesLengthWhenByte() throws Exception {
    HttpRequest request = HttpRequest.builder()
        .uri(getUri() + BYTE)
        .entity(new ByteArrayHttpEntity(REQUEST.getBytes())).build();
    HttpResponse response = send(request);

    assertThat(response.getEntity().getLength().get(), is(equalTo(4L)));
  }

  @Test
  public void propagatesLengthWhenMultipart() throws Exception {
    HttpPart part = new HttpPart("part1", REQUEST.getBytes(), "text/plain", 5);
    HttpRequest request = HttpRequest.builder()
        .uri(getUri() + MULTIPART)
        .addHeader(CONTENT_TYPE, "multipart/form-data; boundary=\"bounds\"")
        .entity(new MultipartHttpEntity(singletonList(part)))
        .build();
    HttpResponse response = send(request);

    assertThat(response.getEntity().getLength().get(), is(equalTo(102L)));
  }

  @Test
  public void propagatesLengthWhenEmpty() throws Exception {
    HttpRequest request = HttpRequest.builder().uri(getUri() + "/empty").build();
    HttpResponse response = send(request);

    assertThat(response.getEntity().getLength().get(), is(equalTo(0L)));
  }

  @Test
  public void propagatesLengthWhenStream() throws Exception {
    HttpRequest request = HttpRequest.builder()
        .uri(getUri() + STREAM)
        .entity(new InputStreamHttpEntity(new ByteArrayInputStream(REQUEST.getBytes()), 5L))
        .build();
    HttpResponse response = send(request);

    assertThat(response.getEntity().getLength().get(), is(equalTo(4L)));
  }

  @Test
  public void doesNotPropagateLengthWhenChunked() throws Exception {
    HttpRequest request = HttpRequest.builder()
        .uri(getUri() + CHUNKED)
        .entity(new InputStreamHttpEntity(new ByteArrayInputStream(REQUEST.getBytes())))
        .build();

    HttpResponse response = send(request);

    assertThat(response.getEntity().getLength(), is(empty()));
  }

  private HttpResponse send(HttpRequest request) throws Exception {
    HttpResponse response = client.send(request, TIMEOUT, false, null);

    assertThat(response.getStatusCode(), is(OK.getStatusCode()));
    return response;
  }

}
