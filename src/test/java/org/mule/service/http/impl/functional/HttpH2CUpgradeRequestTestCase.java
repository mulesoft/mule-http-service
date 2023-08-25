/*
 * Copyright 2023 Salesforce, Inc. All rights reserved.
 */
package org.mule.service.http.impl.functional;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.mule.runtime.http.api.HttpConstants.HttpStatus.OK;
import static org.mule.runtime.http.api.HttpHeaders.Names.UPGRADE;
import static org.mule.service.http.impl.AllureConstants.HttpFeature.HttpStory.LISTENERS;

import java.nio.charset.Charset;

import org.apache.http.client.fluent.Request;
import org.apache.http.entity.ContentType;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mule.runtime.core.api.util.IOUtils;
import org.mule.runtime.http.api.client.HttpClient;
import org.mule.runtime.http.api.client.HttpClientConfiguration;
import org.mule.runtime.http.api.domain.entity.ByteArrayHttpEntity;
import org.mule.runtime.http.api.domain.message.request.HttpRequest;
import org.mule.runtime.http.api.domain.message.response.HttpResponse;
import org.mule.runtime.http.api.domain.message.response.HttpResponseBuilder;
import org.mule.service.http.impl.functional.client.AbstractHttpClientTestCase;

import io.qameta.allure.Story;

@Story(LISTENERS)
public class HttpH2CUpgradeRequestTestCase extends AbstractHttpClientTestCase {

  private static final String RESPONSE = "response";

  private static final String REQUEST = "tests";

  private HttpClient client;

  public HttpH2CUpgradeRequestTestCase(String serviceToLoad) {
    super(serviceToLoad);
  }

  @Before
  public void createClient() {
    HttpClientConfiguration clientConf = new HttpClientConfiguration.Builder().setName(getClass().getSimpleName()).build();
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
    HttpResponseBuilder builder = HttpResponse.builder();
    builder.entity(new ByteArrayHttpEntity(RESPONSE.getBytes()));
    return builder.build();
  }

  @Test
  public void whenSettingUpgradeHeaderDoesNotTimeOut() throws Exception {
    Request request = Request.Post(getUri())
        .bodyString(new String(REQUEST.getBytes()), ContentType.create("text/plain", Charset.forName("UTF-8")))
        .addHeader(UPGRADE, "h2c");
    org.apache.http.HttpResponse response = request.execute().returnResponse();
    assertThat(response.getStatusLine().getStatusCode(), is(OK.getStatusCode()));
    assertThat(IOUtils.toString(response.getEntity().getContent()), is(RESPONSE));
  }

}
