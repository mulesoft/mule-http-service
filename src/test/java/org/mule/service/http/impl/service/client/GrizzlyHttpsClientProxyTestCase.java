/*
 * Copyright 2023 Salesforce, Inc. All rights reserved.
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.service.http.impl.service.client;

import static org.mule.runtime.http.api.HttpConstants.HttpStatus.INTERNAL_SERVER_ERROR;
import static org.mule.runtime.http.api.HttpConstants.HttpStatus.OK;
import static org.mule.runtime.http.api.HttpConstants.Method.POST;
import static org.mule.runtime.http.api.HttpConstants.Protocol.HTTP;
import static org.mule.runtime.http.api.HttpConstants.Protocol.HTTPS;

import static java.security.Security.insertProviderAt;

import static com.ning.http.client.AsyncHttpClientConfigDefaults.ASYNC_CLIENT;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;

import org.mule.rules.BouncyCastleProviderCleaner;
import org.mule.runtime.api.tls.TlsContextFactory;
import org.mule.runtime.http.api.client.HttpClient;
import org.mule.runtime.http.api.client.HttpClientConfiguration;
import org.mule.runtime.http.api.domain.message.request.HttpRequest;
import org.mule.runtime.http.api.domain.message.response.HttpResponse;
import org.mule.runtime.http.api.domain.message.response.HttpResponseBuilder;
import org.mule.runtime.http.api.server.HttpServerConfiguration;
import org.mule.service.http.impl.functional.client.AbstractHttpClientTestCase;
import org.mule.tck.http.TestProxyServer;
import org.mule.tck.junit4.rule.DynamicPort;
import org.mule.tck.junit4.rule.SystemProperty;

import io.qameta.allure.Description;
import io.qameta.allure.Issue;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.jsse.provider.BouncyCastleJsseProvider;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;

@Ignore("W-17059320")
public class GrizzlyHttpsClientProxyTestCase extends AbstractHttpClientTestCase {

  private static final String PASS = "mulepassword";
  private static final String PROXY_AUTHORIZATION_HEADER = "Proxy-Authorization";

  @Rule
  public DynamicPort proxyPort = new DynamicPort("proxyPort");
  @Rule
  public SystemProperty useProxyProperties = new SystemProperty(ASYNC_CLIENT + "useProxyProperties", "true");
  @Rule
  public SystemProperty proxyHostProperty = new SystemProperty("http.proxyHost", "localhost");
  @Rule
  public SystemProperty proxyPortProperty = new SystemProperty("http.proxyPort", proxyPort.getValue());

  private TestProxyServer proxyServer = new TestProxyServer(proxyPort.getNumber(), port.getNumber(), true);
  private HttpClient client;

  @Rule
  public BouncyCastleProviderCleaner bouncyCastleProviderCleaner = new BouncyCastleProviderCleaner();

  public GrizzlyHttpsClientProxyTestCase(String serviceToLoad) {
    super(serviceToLoad);
  }

  private void ensureBouncyCastleProviders() {
    insertProviderAt(new BouncyCastleProvider(), 1);
    insertProviderAt(new BouncyCastleJsseProvider(), 2);
  }

  @Before
  public void createClient() throws Exception {
    ensureBouncyCastleProviders();
    proxyServer.start();
    client = service.getClientFactory().create(new HttpClientConfiguration.Builder()
        .setTlsContextFactory(TlsContextFactory.builder()
            .trustStorePath("tls/trustStore")
            .trustStorePassword(PASS)
            .build())
        .setName("httpsProxyHeader-test")
        .build());
    client.start();
  }

  @After
  public void stopClient() throws Exception {
    if (proxyServer != null) {
      proxyServer.stop();
    }
    if (client != null) {
      client.stop();
    }
  }

  @Override
  protected HttpResponse setUpHttpResponse(HttpRequest request) {
    assertNull(request.getHeaderValue(PROXY_AUTHORIZATION_HEADER));
    HttpResponseBuilder response = HttpResponse.builder();
    try {
      return response.statusCode(OK.getStatusCode()).build();
    } catch (Exception e) {
      return response.statusCode(INTERNAL_SERVER_ERROR.getStatusCode()).build();
    }
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

  @Test
  @Issue("W-10863931")
  @Description("An HTTPS request with a basic proxy does not have to add an NTLM header.")
  public void basicHttpsRequestDoesNotSendNtlmProxyHeader() throws Exception {
    final HttpResponse response = client.send(HttpRequest.builder()
        .method(POST)
        .uri(getUri())
        .build(), getDefaultOptions(TIMEOUT));

    assertThat(response.getStatusCode(), is(OK.getStatusCode()));
  }
}
