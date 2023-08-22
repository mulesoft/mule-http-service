/*
 * Copyright 2023 Salesforce, Inc. All rights reserved.
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
import static org.mule.service.http.impl.AllureConstants.HttpFeature.HttpStory.MULTIPART;
import org.mule.runtime.core.api.util.IOUtils;
import org.mule.runtime.http.api.domain.entity.multipart.HttpPart;
import org.mule.runtime.http.api.domain.entity.multipart.MultipartHttpEntity;
import org.mule.runtime.http.api.domain.message.response.HttpResponse;

import io.qameta.allure.Story;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.junit.Before;
import org.junit.Test;

@Story(MULTIPART)
public class HttpServerMixedPartTestCase extends AbstractHttpServerTestCase {

  private static final String MIXED_CONTENT =
      "--the-boundary\r\n"
          + "Content-Type: text/plain\r\n"
          + "Custom: myHeader\r\n"
          + "Content-Disposition: attachment; name=\"field\"\r\n"
          + "\r\n"
          + "My data here\r\n"
          + "--the-boundary--\r\n";

  public HttpServerMixedPartTestCase(String serviceToLoad) {
    super(serviceToLoad);
  }

  @Before
  public void setUp() throws Exception {
    setUpServer();
    server.addRequestHandler("/", (requestContext, responseCallback) -> {
      String data = "My data here";
      HttpPart part = new HttpPart("field", data.getBytes(), TEXT.toRfcString(), data.length());
      part.addHeader("Custom", "myHeader");
      responseCallback.responseReady(HttpResponse.builder().entity(new MultipartHttpEntity(singletonList(part)))
          .addHeader(CONTENT_TYPE, MULTIPART_MIXED.toRfcString() + "; boundary=\"the-boundary\"")
          .build(), new IgnoreResponseStatusCallback());

    });
  }

  @Override
  protected String getServerName() {
    return "parts-test";
  }

  @Test
  public void handlesMultipartMixed() throws Exception {
    try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
      HttpGet httpPost = new HttpGet("http://localhost:" + port.getValue());
      try (CloseableHttpResponse response = httpClient.execute(httpPost)) {
        assertThat(IOUtils.toString(response.getEntity().getContent()), is(equalTo(MIXED_CONTENT)));
      }
    }
  }


}
