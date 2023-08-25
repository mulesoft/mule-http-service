/*
 * Copyright 2023 Salesforce, Inc. All rights reserved.
 */
package org.mule.service.http.impl.functional.client;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.mule.runtime.http.api.HttpConstants.HttpStatus.OK;
import static org.mule.service.http.impl.AllureConstants.HttpFeature.HttpStory.PROXIES;
import org.mule.runtime.core.api.util.IOUtils;
import org.mule.runtime.http.api.client.HttpClient;
import org.mule.runtime.http.api.client.HttpClientConfiguration;
import org.mule.runtime.http.api.client.HttpRequestOptions;
import org.mule.runtime.http.api.client.proxy.ProxyConfig;
import org.mule.runtime.http.api.domain.entity.ByteArrayHttpEntity;
import org.mule.runtime.http.api.domain.message.request.HttpRequest;
import org.mule.runtime.http.api.domain.message.response.HttpResponse;
import org.mule.runtime.http.api.server.HttpServer;
import org.mule.runtime.http.api.server.HttpServerConfiguration;
import org.mule.tck.http.TestProxyServer;
import org.mule.tck.junit4.rule.DynamicPort;

import io.qameta.allure.Story;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

@Story(PROXIES)
public class HttpClientProxyTestCase extends AbstractHttpClientTestCase {

  private static final String GLOBAL_RESPONSE = "Global proxy used";
  private static final String REQUEST_RESPONSE = "Request proxy used";

  @Rule
  public DynamicPort globalProxyPort = new DynamicPort("globalProxyPort");
  @Rule
  public DynamicPort requestProxyPort = new DynamicPort("requestProxyPort");
  @Rule
  public DynamicPort serverPort = new DynamicPort("serverPort");

  private TestProxyServer globalProxy = new TestProxyServer(globalProxyPort.getNumber(), port.getNumber(), false);
  private TestProxyServer requestProxy = new TestProxyServer(requestProxyPort.getNumber(), serverPort.getNumber(), false);
  private HttpClient client;
  private HttpServer requestServer;

  public HttpClientProxyTestCase(String serviceToLoad) {
    super(serviceToLoad);
  }

  @Override
  protected HttpResponse setUpHttpResponse(HttpRequest request) {
    return HttpResponse.builder()
        .entity(new ByteArrayHttpEntity(GLOBAL_RESPONSE.getBytes()))
        .build();
  }

  @Before
  public void createClient() throws Exception {
    requestServer = service.getServerFactory().create(new HttpServerConfiguration.Builder()
        .setHost("localhost")
        .setPort(serverPort.getNumber())
        .setName("proxy-test-server")
        .build());
    requestServer.start();
    requestServer.addRequestHandler("/*",
                                    (requestContext, responseCallback) -> responseCallback
                                        .responseReady(HttpResponse.builder()
                                            .entity(new ByteArrayHttpEntity(REQUEST_RESPONSE.getBytes()))
                                            .build(),
                                                       new IgnoreResponseStatusCallback()));
    globalProxy.start();
    requestProxy.start();
    client = service.getClientFactory().create(new HttpClientConfiguration.Builder()
        .setName("proxy-test")
        .setProxyConfig(ProxyConfig.builder()
            .host("localhost")
            .port(globalProxyPort.getNumber())
            .build())
        .build());
    client.start();
  }

  @After
  public void stopClient() throws Exception {
    if (requestServer != null) {
      requestServer.stop();
    }
    if (requestProxy != null) {
      requestProxy.stop();
    }
    if (globalProxy != null) {
      globalProxy.stop();
    }
    if (client != null) {
      client.stop();
    }
  }

  @Test
  public void usesDefaultProxy() throws Exception {
    HttpResponse response = client.send(HttpRequest.builder().uri(getUri()).build(), getDefaultOptions(TIMEOUT));

    assertThat(globalProxy.hasConnections(), is(true));
    assertThat(requestProxy.hasConnections(), is(false));
    assertThat(response.getStatusCode(), is(OK.getStatusCode()));
    assertThat(IOUtils.toString(response.getEntity().getContent()), is(GLOBAL_RESPONSE));
  }

  @Test
  public void overridesDefaultProxy() throws Exception {
    HttpResponse response = client.send(HttpRequest.builder()
        .uri("http://localhost:" + serverPort.getValue())
        .build(),
                                        HttpRequestOptions.builder()
                                            .responseTimeout(TIMEOUT)
                                            .proxyConfig(ProxyConfig.builder()
                                                .host("localhost")
                                                .port(requestProxyPort.getNumber())
                                                .build())
                                            .build());

    assertThat(globalProxy.hasConnections(), is(false));
    assertThat(requestProxy.hasConnections(), is(true));
    assertThat(response.getStatusCode(), is(OK.getStatusCode()));
    assertThat(IOUtils.toString(response.getEntity().getContent()), is(REQUEST_RESPONSE));
  }

}
