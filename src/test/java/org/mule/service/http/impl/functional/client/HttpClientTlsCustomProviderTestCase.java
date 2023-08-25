/*
 * Copyright 2023 Salesforce, Inc. All rights reserved.
 */
package org.mule.service.http.impl.functional.client;

import static org.mule.runtime.api.metadata.MediaType.JSON;
import static org.mule.runtime.http.api.HttpConstants.HttpStatus.INTERNAL_SERVER_ERROR;
import static org.mule.runtime.http.api.HttpConstants.HttpStatus.OK;
import static org.mule.runtime.http.api.HttpConstants.Method.POST;
import static org.mule.runtime.http.api.HttpConstants.Protocol.HTTP;
import static org.mule.runtime.http.api.HttpConstants.Protocol.HTTPS;
import static org.mule.service.http.impl.AllureConstants.HttpFeature.HttpStory.TLS;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.security.Security.insertProviderAt;
import static java.util.Collections.singletonList;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertThat;

import io.qameta.allure.Issue;
import org.junit.Rule;
import org.mule.rules.BouncyCastleProviderCleaner;
import org.mule.runtime.api.lifecycle.CreateException;
import org.mule.runtime.api.tls.TlsContextFactory;
import org.mule.runtime.core.api.util.IOUtils;
import org.mule.runtime.http.api.client.HttpClient;
import org.mule.runtime.http.api.client.HttpClientConfiguration;
import org.mule.runtime.http.api.domain.entity.ByteArrayHttpEntity;
import org.mule.runtime.http.api.domain.entity.multipart.HttpPart;
import org.mule.runtime.http.api.domain.entity.multipart.MultipartHttpEntity;
import org.mule.runtime.http.api.domain.message.request.HttpRequest;
import org.mule.runtime.http.api.domain.message.response.HttpResponse;
import org.mule.runtime.http.api.domain.message.response.HttpResponseBuilder;
import org.mule.runtime.http.api.server.HttpServerConfiguration;

import io.qameta.allure.Description;
import io.qameta.allure.Story;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.jsse.provider.BouncyCastleJsseProvider;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import java.io.IOException;
import java.util.Collection;

@Story(TLS)
@Issue("MULE-18398")
public class HttpClientTlsCustomProviderTestCase extends AbstractHttpClientTestCase {

  private static final String PASS = "mulepassword";

  private byte[] dataBytes = "{ \'I am a JSON attachment!\' }".getBytes(UTF_8);
  private HttpClient client;

  @Rule
  public BouncyCastleProviderCleaner bouncyCastleProviderCleaner = new BouncyCastleProviderCleaner();

  public HttpClientTlsCustomProviderTestCase(String serviceToLoad) {
    super(serviceToLoad);
  }

  private void ensureBouncyCastleProviders() {
    insertProviderAt(new BouncyCastleProvider(), 1);
    insertProviderAt(new BouncyCastleJsseProvider(), 2);
  }

  @Before
  public void createClient() throws CreateException {
    ensureBouncyCastleProviders();

    client = service.getClientFactory().create(new HttpClientConfiguration.Builder()
        .setTlsContextFactory(TlsContextFactory.builder()
            .trustStorePath("tls/trustStore")
            .trustStorePassword(PASS)
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
  @Description("Send request using custom TLS provider (BC).")
  public void sendRequestUsingCustomTlsProvider() throws Exception {
    HttpPart part = new HttpPart("someJson", dataBytes, JSON.toRfcString(), dataBytes.length);
    MultipartHttpEntity multipart = new MultipartHttpEntity(singletonList(part));
    final HttpResponse response = client.send(HttpRequest.builder()
        .method(POST)
        .uri(getUri())
        .entity(multipart)
        .build(), getDefaultOptions(TIMEOUT));
    assertThat(IOUtils.toString(response.getEntity().getContent()), is(equalTo("OK")));
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

}
