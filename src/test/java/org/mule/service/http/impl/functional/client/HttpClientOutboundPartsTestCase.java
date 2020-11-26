/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.service.http.impl.functional.client;

import static java.util.Collections.singletonList;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.mule.runtime.http.api.HttpConstants.HttpStatus.INTERNAL_SERVER_ERROR;
import static org.mule.runtime.http.api.HttpConstants.HttpStatus.OK;
import static org.mule.runtime.http.api.HttpConstants.Protocol.HTTP;
import static org.mule.runtime.http.api.HttpConstants.Protocol.HTTPS;
import static org.mule.service.http.impl.AllureConstants.HttpFeature.HttpStory.MULTIPART;
import org.mule.runtime.api.lifecycle.CreateException;
import org.mule.runtime.api.tls.TlsContextFactory;
import org.mule.runtime.http.api.HttpConstants;
import org.mule.runtime.http.api.client.HttpClient;
import org.mule.runtime.http.api.client.HttpClientConfiguration;
import org.mule.runtime.http.api.domain.entity.ByteArrayHttpEntity;
import org.mule.runtime.http.api.domain.entity.multipart.HttpPart;
import org.mule.runtime.http.api.domain.entity.multipart.MultipartHttpEntity;
import org.mule.runtime.http.api.domain.message.request.HttpRequest;
import org.mule.runtime.http.api.domain.message.response.HttpResponse;
import org.mule.runtime.http.api.domain.message.response.HttpResponseBuilder;
import org.mule.runtime.http.api.server.HttpServerConfiguration;
import org.mule.runtime.http.api.tcp.TcpClientSocketProperties;

import java.util.Collection;

import io.qameta.allure.Story;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

@Story(MULTIPART)
public class HttpClientOutboundPartsTestCase extends AbstractHttpClientTestCase {

  private static final String PASS = "mulepassword";
  private static final int SEND_BUFFER_SIZE = 128;
  private static final String TEXT_PLAIN = "text/plain";

  private HttpClient client;

  public HttpClientOutboundPartsTestCase(String serviceToLoad) {
    super(serviceToLoad);
  }

  @Before
  public void createClient() throws CreateException {
    client = service.getClientFactory().create(new HttpClientConfiguration.Builder()
        .setTlsContextFactory(TlsContextFactory.builder()
            .trustStorePath("tls/trustStore")
            .trustStorePassword(PASS)
            .build())
        .setClientSocketProperties(TcpClientSocketProperties.builder()
            .sendBufferSize(SEND_BUFFER_SIZE)
            .build())
        .setName("multipart-test")
        .build());
    client.start();
  }

  @After
  public void stopClient() {
    if (client != null) {
      client.stop();
    }
  }

  @Test
  public void sendingAttachmentBiggerThanAsyncWriteQueueSizeWorksOverHttps() throws Exception {
    // Grizzly defines the maxAsyncWriteQueueSize as 4 times the sendBufferSize
    // (org.glassfish.grizzly.nio.transport.TCPNIOConnection).
    int maxAsyncWriteQueueSize = SEND_BUFFER_SIZE * 4;
    int size = maxAsyncWriteQueueSize * 2;
    HttpPart part = new HttpPart("part1", new byte[size], TEXT_PLAIN, size);

    HttpResponse response = client.send(HttpRequest.builder()
        .method(HttpConstants.Method.POST)
        .uri(getUri())
        .entity(new MultipartHttpEntity(singletonList(part)))
        .build(), getDefaultOptions(TIMEOUT));

    assertThat(response.getStatusCode(), is(OK.getStatusCode()));
  }

  @Override
  protected HttpServerConfiguration.Builder getServerConfigurationBuilder() throws Exception {
    return super.getServerConfigurationBuilder().setTlsContextFactory(TlsContextFactory.builder()
        .keyStorePath("tls/serverKeystore")
        .keyStorePassword(PASS)
        .keyPassword(PASS)
        .build());
  }

  @Override
  protected String getUri() {
    return super.getUri().replace(HTTP.getScheme(), HTTPS.getScheme());
  }

  @Override
  protected HttpResponse setUpHttpResponse(HttpRequest request) {
    HttpResponseBuilder response = HttpResponse.builder();
    try {
      Collection<HttpPart> parts = request.getEntity().getParts();
      assertThat(parts, hasSize(1));
      HttpPart part = parts.iterator().next();
      assertThat(part.getName(), is("part1"));
      assertThat(part.getContentType(), is(TEXT_PLAIN));
      return response.statusCode(OK.getStatusCode()).entity(new ByteArrayHttpEntity(OK.getReasonPhrase().getBytes())).build();
    } catch (Exception e) {
      // Move on
    }

    return response.statusCode(INTERNAL_SERVER_ERROR.getStatusCode()).build();
  }

}
