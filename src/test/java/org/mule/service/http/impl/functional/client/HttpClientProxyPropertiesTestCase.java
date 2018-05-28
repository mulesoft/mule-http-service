/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.service.http.impl.functional.client;

import static com.ning.http.client.AsyncHttpClientConfigDefaults.ASYNC_CLIENT;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.mule.runtime.http.api.HttpConstants.HttpStatus.OK;
import static org.mule.service.http.impl.AllureConstants.HttpFeature.HttpStory.PROXIES;
import org.mule.runtime.http.api.client.HttpClient;
import org.mule.runtime.http.api.client.HttpClientConfiguration;
import org.mule.runtime.http.api.domain.message.request.HttpRequest;
import org.mule.runtime.http.api.domain.message.response.HttpResponse;
import org.mule.tck.http.TestProxyServer;
import org.mule.tck.junit4.rule.DynamicPort;
import org.mule.tck.junit4.rule.SystemProperty;

import io.qameta.allure.Story;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

@Story(PROXIES)
public class HttpClientProxyPropertiesTestCase extends AbstractHttpClientTestCase {

  @Rule
  public DynamicPort proxyPort = new DynamicPort("proxyPort");
  @Rule
  public SystemProperty useProxyProperties = new SystemProperty(ASYNC_CLIENT + "useProxyProperties", "true");
  @Rule
  public SystemProperty proxyHostProperty = new SystemProperty("http.proxyHost", "localhost");
  @Rule
  public SystemProperty proxyPortProperty = new SystemProperty("http.proxyPort", proxyPort.getValue());

  private TestProxyServer proxyServer = new TestProxyServer(proxyPort.getNumber(), port.getNumber(), false);
  private HttpClient client;

  public HttpClientProxyPropertiesTestCase(String serviceToLoad) {
    super(serviceToLoad);
  }

  @Override
  protected HttpResponse setUpHttpResponse(HttpRequest request) {
    return HttpResponse.builder().statusCode(OK.getStatusCode()).build();
  }

  @Before
  public void createClient() throws Exception {
    proxyServer.start();
    client = service.getClientFactory().create(new HttpClientConfiguration.Builder().setName("proxy-properties-test").build());
    client.start();
  }

  @Test
  public void usesProxyWithoutExplicitConfig() throws Exception {
    HttpResponse response = client.send(HttpRequest.builder().uri(getUri()).build(), getDefaultOptions(TIMEOUT));
    assertThat(proxyServer.hasConnections(), is(true));
    assertThat(response.getStatusCode(), is(OK.getStatusCode()));
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

}
