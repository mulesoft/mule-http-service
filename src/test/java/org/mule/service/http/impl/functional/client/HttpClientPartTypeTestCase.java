/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.service.http.impl.functional.client;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Collections.singletonList;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.mule.runtime.api.metadata.MediaType.JSON;
import static org.mule.runtime.http.api.HttpConstants.HttpStatus.INTERNAL_SERVER_ERROR;
import static org.mule.runtime.http.api.HttpConstants.HttpStatus.OK;
import static org.mule.runtime.http.api.HttpConstants.Method.POST;
import static org.mule.service.http.impl.AllureConstants.HttpFeature.HTTP_SERVICE;
import static org.mule.service.http.impl.AllureConstants.HttpFeature.HttpStory.MULTIPART;
import org.mule.runtime.core.api.util.IOUtils;
import org.mule.runtime.http.api.client.HttpClient;
import org.mule.runtime.http.api.client.HttpClientConfiguration;
import org.mule.runtime.http.api.domain.entity.ByteArrayHttpEntity;
import org.mule.runtime.http.api.domain.entity.multipart.HttpPart;
import org.mule.runtime.http.api.domain.entity.multipart.MultipartHttpEntity;
import org.mule.runtime.http.api.domain.message.request.HttpRequest;
import org.mule.runtime.http.api.domain.message.response.HttpResponse;
import org.mule.runtime.http.api.domain.message.response.HttpResponseBuilder;

import java.io.IOException;
import java.util.Collection;

import io.qameta.allure.Description;
import io.qameta.allure.Feature;
import io.qameta.allure.Story;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

@Feature(HTTP_SERVICE)
@Story(MULTIPART)
public class HttpClientPartTypeTestCase extends AbstractHttpClientTestCase {

  private byte[] dataBytes = "{ \'I am a JSON attachment!\' }".getBytes(UTF_8);
  private HttpClient client;

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
    HttpResponseBuilder response = HttpResponse.builder();
    try {
      Collection<HttpPart> parts = request.getEntity().getParts();
      if (parts.size() == 1 && parts.stream().anyMatch(part -> JSON.toRfcString().equals(part.getContentType()))) {
        return response.statusCode(OK.getStatusCode()).entity(new ByteArrayHttpEntity(OK.getReasonPhrase().getBytes())).build();
      }
    } catch (IOException e) {
      // Move on
    }

    return response.statusCode(INTERNAL_SERVER_ERROR.getStatusCode()).build();
  }

  @Test
  @Description("Verify that parts content type is preserved.")
  public void partContentTypeIsPreserved() throws Exception {
    HttpPart part = new HttpPart("someJson", dataBytes, JSON.toRfcString(), dataBytes.length);
    MultipartHttpEntity multipart = new MultipartHttpEntity(singletonList(part));
    final HttpResponse response = client.send(HttpRequest.builder()
        .method(POST)
        .uri(getUri())
        .entity(multipart)
        .build(), TIMEOUT, true, null);
    assertThat(IOUtils.toString(response.getEntity().getContent()), is(equalTo("OK")));
  }


}
