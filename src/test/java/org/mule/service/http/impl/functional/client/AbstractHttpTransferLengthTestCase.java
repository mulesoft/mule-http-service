/*
 * Copyright 2023 Salesforce, Inc. All rights reserved.
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.service.http.impl.functional.client;

import static org.mule.runtime.http.api.HttpConstants.HttpStatus.BAD_REQUEST;
import static org.mule.runtime.http.api.HttpConstants.HttpStatus.OK;
import static org.mule.runtime.http.api.HttpHeaders.Names.CONTENT_LENGTH;
import static org.mule.runtime.http.api.HttpHeaders.Names.CONTENT_TYPE;
import static org.mule.service.http.impl.AllureConstants.HttpFeature.HttpStory.TRANSFER_TYPE;
import static org.mule.service.http.impl.service.server.grizzly.GrizzlyServerManager.ALLOW_PAYLOAD_FOR_UNDEFINED_METHODS;

import static java.lang.Long.valueOf;
import static java.util.Collections.singletonList;
import static java.util.OptionalLong.empty;
import static java.util.OptionalLong.of;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;

import org.mule.runtime.http.api.client.HttpClient;
import org.mule.runtime.http.api.client.HttpClientConfiguration;
import org.mule.runtime.http.api.domain.entity.ByteArrayHttpEntity;
import org.mule.runtime.http.api.domain.entity.EmptyHttpEntity;
import org.mule.runtime.http.api.domain.entity.HttpEntity;
import org.mule.runtime.http.api.domain.entity.InputStreamHttpEntity;
import org.mule.runtime.http.api.domain.entity.multipart.HttpPart;
import org.mule.runtime.http.api.domain.entity.multipart.MultipartHttpEntity;
import org.mule.runtime.http.api.domain.message.request.HttpRequest;
import org.mule.runtime.http.api.domain.message.response.HttpResponse;
import org.mule.runtime.http.api.domain.message.response.HttpResponseBuilder;
import org.mule.service.http.impl.service.domain.entity.multipart.StreamedMultipartHttpEntity;

import java.io.ByteArrayInputStream;
import java.util.OptionalLong;
import java.util.function.Consumer;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import io.qameta.allure.Story;

@Story(TRANSFER_TYPE)
@RunWith(Parameterized.class)
public abstract class AbstractHttpTransferLengthTestCase extends AbstractHttpClientTestCase {

  private static final String RESPONSE = "TEST";
  private static final String REQUEST = "tests";
  private static final String BYTE = "/byte";
  private static final String MULTIPART = "/multipart";
  private static final String STREAM = "/stream";
  private static final String CHUNKED = "/chunked";

  private HttpClient client;

  private final boolean isAllowPayloadDefault;

  protected AbstractHttpTransferLengthTestCase(String serviceToLoad, boolean isAllowPayload) {
    super(serviceToLoad);
    isAllowPayloadDefault = ALLOW_PAYLOAD_FOR_UNDEFINED_METHODS;
    ALLOW_PAYLOAD_FOR_UNDEFINED_METHODS = isAllowPayload;
  }

  @Before
  public void createClient() {
    HttpClientConfiguration clientConf = new HttpClientConfiguration.Builder().setName("transfer-type-test").build();
    client = service.getClientFactory().create(clientConf);
    client.start();
  }

  @Override
  @After
  public void tearDown() {
    if (client != null) {
      client.stop();
    }
    ALLOW_PAYLOAD_FOR_UNDEFINED_METHODS = isAllowPayloadDefault;
  }

  @Override
  protected HttpResponse setUpHttpResponse(HttpRequest request) {
    String path = request.getPath();
    HttpEntity entity = request.getEntity();
    HttpResponseBuilder builder = HttpResponse.builder();
    try {
      OptionalLong expectedRequestLength = of(valueOf(REQUEST.length()));
      if (BYTE.equals(path)) {
        assertThat(request.containsHeader(CONTENT_LENGTH), is(true));
        assertThat(entity, is(instanceOf(InputStreamHttpEntity.class)));

        builder.entity(new ByteArrayHttpEntity(RESPONSE.getBytes()));
      } else if (MULTIPART.equals(path)) {
        assertThat(request.containsHeader(CONTENT_LENGTH), is(true));
        expectedRequestLength = of(142L);
        assertThat(entity, is(instanceOf(StreamedMultipartHttpEntity.class)));

        HttpPart part = new HttpPart("part1", "TEST".getBytes(), "text/plain", 4);
        builder
            .entity(new MultipartHttpEntity(singletonList(part)))
            .addHeader(CONTENT_TYPE, "multipart/form-data; boundary=\"bounds\"");
      } else if (STREAM.equals(path)) {
        assertThat(request.containsHeader(CONTENT_LENGTH), is(true));
        assertThat(entity, is(instanceOf(InputStreamHttpEntity.class)));

        builder.entity(new InputStreamHttpEntity(new ByteArrayInputStream("TEST".getBytes()), 4L));
      } else if (CHUNKED.equals(path)) {
        assertThat(request.containsHeader(CONTENT_LENGTH), is(false));
        assertThat(entity, is(instanceOf(InputStreamHttpEntity.class)));
        expectedRequestLength = empty();

        builder.entity(new InputStreamHttpEntity(new ByteArrayInputStream("TEST".getBytes())));
      } else { // empty request
        assertThat(request.containsHeader(CONTENT_LENGTH), is(false));
        expectedRequestLength = of(0L);
        assertThat(entity, is(instanceOf(EmptyHttpEntity.class)));
      }
      assertThat(request.getEntity().getBytesLength(), is(expectedRequestLength));
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
    send(request, false, response -> {
      assertThat(response.getEntity().getBytesLength().getAsLong(), is(equalTo(4L)));
      assertThat(response.getEntity(), instanceOf(InputStreamHttpEntity.class));
    });
  }

  @Test
  public void propagatesLengthWhenMultipart() throws Exception {
    HttpPart part = new HttpPart("part1", REQUEST.getBytes(), "text/plain", 5);
    HttpRequest request = HttpRequest.builder()
        .uri(getUri() + MULTIPART)
        .addHeader(CONTENT_TYPE, "multipart/form-data; boundary=\"bounds\"")
        .entity(new MultipartHttpEntity(singletonList(part)))
        .build();
    send(request, false, response -> {
      assertThat(response.getEntity().getBytesLength().getAsLong(), is(equalTo(102L)));
      assertThat(response.getEntity(), instanceOf(StreamedMultipartHttpEntity.class));
    });
  }

  @Test
  public void propagatesLengthWhenEmpty() throws Exception {
    HttpRequest request = HttpRequest.builder().uri(getUri() + "/empty").build();
    send(request, true, response -> {
      assertThat(response.getEntity().getBytesLength().getAsLong(), is(equalTo(0L)));
      assertThat(response.getEntity(), instanceOf(EmptyHttpEntity.class));
    });
  }

  @Test
  public void propagatesLengthWhenStream() throws Exception {
    HttpRequest request = HttpRequest.builder()
        .uri(getUri() + STREAM)
        .entity(new InputStreamHttpEntity(new ByteArrayInputStream(REQUEST.getBytes()), 5L))
        .build();
    send(request, false, response -> {
      assertThat(response.getEntity().getBytesLength().getAsLong(), is(equalTo(4L)));
      assertThat(response.getEntity(), instanceOf(InputStreamHttpEntity.class));
    });
  }

  @Test
  public void doesNotPropagateLengthWhenChunked() throws Exception {
    HttpRequest request = HttpRequest.builder()
        .uri(getUri() + CHUNKED)
        .entity(new InputStreamHttpEntity(new ByteArrayInputStream(REQUEST.getBytes())))
        .build();

    send(request, false, response -> {
      assertThat(response.getEntity().getBytesLength(), is(OptionalLong.empty()));
      assertThat(response.getEntity(), instanceOf(InputStreamHttpEntity.class));
    });
  }

  private void send(HttpRequest request, boolean hasEmptyPayload, Consumer<HttpResponse> onSuccessResponse) throws Exception {
    HttpResponse response = client.send(request, getDefaultOptions(TIMEOUT));

    if (ALLOW_PAYLOAD_FOR_UNDEFINED_METHODS || hasEmptyPayload) {
      assertThat(response.getStatusCode(), is(OK.getStatusCode()));
      onSuccessResponse.accept(response);
    } else {
      assertThat(response.getStatusCode(), is(BAD_REQUEST.getStatusCode()));
    }
  }

}
