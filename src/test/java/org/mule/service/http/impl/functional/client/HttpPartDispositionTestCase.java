/*
 * Copyright 2023 Salesforce, Inc. All rights reserved.
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.service.http.impl.functional.client;

import static java.lang.Boolean.FALSE;
import static java.lang.Boolean.TRUE;
import static java.lang.String.format;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.mule.runtime.api.metadata.MediaType.TEXT;
import static org.mule.runtime.http.api.HttpConstants.HttpStatus.OK;
import static org.mule.runtime.http.api.HttpHeaders.Names.CONTENT_TYPE;
import static org.mule.service.http.impl.AllureConstants.HttpFeature.HttpStory.MULTIPART;
import org.mule.runtime.api.util.MultiMap;
import org.mule.runtime.core.api.util.IOUtils;
import org.mule.runtime.http.api.client.HttpClient;
import org.mule.runtime.http.api.client.HttpClientConfiguration;
import org.mule.runtime.http.api.domain.entity.ByteArrayHttpEntity;
import org.mule.runtime.http.api.domain.entity.multipart.HttpPart;
import org.mule.runtime.http.api.domain.message.request.HttpRequest;
import org.mule.runtime.http.api.domain.message.response.HttpResponse;
import org.mule.runtime.http.api.domain.message.response.HttpResponseBuilder;

import java.util.Collection;

import io.qameta.allure.Story;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

@Story(MULTIPART)
public class HttpPartDispositionTestCase extends AbstractHttpClientTestCase {

  private static final String BOUNDARY = "bec89590-35fe-11e5-a966-de100cec9c0d";
  private static final String CONTENT_DISPOSITION_HEADER = "Content-Disposition: form-data; name=\"partName\"\r\n";
  private static final String MULTIPART_FORMAT = "--%1$s\r\n %2$sContent-Type: text/plain\n\r\ntest\r\n--%1$s--\r\n";
  private static final String CONTENT_DISPOSITION_PARAM = "contentDisposition";

  private HttpClient client;

  public HttpPartDispositionTestCase(String serviceToLoad) {
    super(serviceToLoad);
  }

  @Before
  public void createClient() {
    client = service.getClientFactory().create(new HttpClientConfiguration.Builder().setName("multipart-test").build());
    client.start();
  }

  @Override
  protected HttpResponse setUpHttpResponse(HttpRequest request) {
    HttpResponseBuilder response = HttpResponse.builder();
    String contentDispositionHeader;
    if (TRUE.toString().equals(request.getQueryParams().get(CONTENT_DISPOSITION_PARAM))) {
      contentDispositionHeader = CONTENT_DISPOSITION_HEADER;
    } else {
      contentDispositionHeader = "";
    }
    byte[] body = format(MULTIPART_FORMAT, BOUNDARY, contentDispositionHeader).getBytes();
    response.addHeader(CONTENT_TYPE, format("multipart/form-data; boundary=%s", BOUNDARY));
    response.statusCode(OK.getStatusCode());
    response.entity(new ByteArrayHttpEntity(body));
    return response.build();
  }

  @After
  public void stopClient() {
    if (client != null) {
      client.stop();
    }
  }

  @Test
  public void receivesMultipartContentWithContentDisposition() throws Exception {
    testWithContentDisposition(TRUE);
  }

  @Test
  public void receivesMultipartContentWithoutContentDisposition() throws Exception {
    testWithContentDisposition(FALSE);
  }

  private void testWithContentDisposition(Boolean addHeader) throws Exception {
    MultiMap<String, String> queryParams = new MultiMap<>();
    queryParams.put(CONTENT_DISPOSITION_PARAM, addHeader.toString());
    HttpResponse response =
        client.send(HttpRequest.builder().uri(getUri()).queryParams(queryParams).build(), getDefaultOptions(TIMEOUT));
    assertThat(response.getEntity().isComposed(), is(true));
    Collection<HttpPart> parts = response.getEntity().getParts();
    assertThat(parts, hasSize(1));
    HttpPart part = parts.iterator().next();
    assertThat(part.getContentType(), is(TEXT.toRfcString()));
    assertThat(IOUtils.toString(part.getInputStream()), is("test"));
    if (addHeader) {
      assertThat(part.getName(), is("partName"));
    }
  }

}
