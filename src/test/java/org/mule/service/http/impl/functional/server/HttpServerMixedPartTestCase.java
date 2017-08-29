/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.service.http.impl.functional.server;

import static java.util.Collections.singletonList;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;
import static org.mule.runtime.api.metadata.MediaType.MULTIPART_MIXED;
import static org.mule.runtime.api.metadata.MediaType.TEXT;
import static org.mule.runtime.http.api.HttpHeaders.Names.CONTENT_TYPE;
import static org.mule.runtime.http.api.HttpHeaders.Names.TRANSFER_ENCODING;
import static org.mule.runtime.http.api.HttpHeaders.Values.CHUNKED;
import static org.mule.service.http.impl.AllureConstants.HttpFeature.HTTP_SERVICE;
import static org.mule.service.http.impl.AllureConstants.HttpFeature.HttpStory.MULTIPART;
import org.mule.runtime.core.api.util.IOUtils;
import org.mule.runtime.http.api.domain.entity.multipart.HttpPart;
import org.mule.runtime.http.api.domain.entity.multipart.MultipartHttpEntity;
import org.mule.runtime.http.api.domain.message.response.HttpResponse;
import org.mule.runtime.http.api.server.HttpServer;
import org.mule.runtime.http.api.server.HttpServerConfiguration;
import org.mule.service.http.impl.functional.AbstractHttpServiceTestCase;
import org.mule.tck.junit4.rule.DynamicPort;

import io.qameta.allure.Feature;
import io.qameta.allure.Story;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

@Feature(HTTP_SERVICE)
@Story(MULTIPART)
public class HttpServerMixedPartTestCase extends AbstractHttpServiceTestCase {

  private static final String MIXED_CONTENT =
      "--the-boundary\n"
          + "Content-Type: text/plain\n"
          + "Custom: myHeader\n"
          + "Content-Disposition: attachment; name=\"field\"\n"
          + "\n"
          + "My data here\n"
          + "--the-boundary--\n";

  @Rule
  public DynamicPort port = new DynamicPort("port");

  private HttpServer server;

  @Before
  public void setUp() throws Exception {
    server = service.getServerFactory().create(new HttpServerConfiguration.Builder()
        .setHost("localhost")
        .setPort(port.getNumber())
        .setName("parts-test")
        .build());
    server.start();
    server.addRequestHandler("/", (requestContext, responseCallback) -> {
      String data = "My data here";
      HttpPart part = new HttpPart("field", data.getBytes(), TEXT.toRfcString(), data.length());
      part.addHeader("Custom", "myHeader");
      responseCallback.responseReady(HttpResponse.builder().entity(new MultipartHttpEntity(singletonList(part)))
          .addHeader(CONTENT_TYPE, MULTIPART_MIXED.toRfcString() + "; boundary=\"the-boundary\"")
          .addHeader(TRANSFER_ENCODING, CHUNKED).build(), new IgnoreResponseStatusCallback());

    });
  }

  @After
  public void tearDown() {
    if (server != null) {
      server.stop();
    }
  }

  @Test
  public void handlesMultipartMixed() throws Exception {
    try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
      HttpGet httpPost = new HttpGet("http://localhost:" + port.getValue());
      try (CloseableHttpResponse response = httpClient.execute(httpPost)) {
        assertThat(IOUtils.toString(response.getEntity().getContent()).replace("\r", ""), is(equalTo(MIXED_CONTENT)));
      }
    }
  }


}
