/*
 * Copyright 2023 Salesforce, Inc. All rights reserved.
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.service.http;

import static java.lang.Integer.MAX_VALUE;
import static org.mule.runtime.api.util.Preconditions.checkArgument;
import static org.mule.runtime.core.api.lifecycle.LifecycleUtils.initialiseIfNeeded;
import org.mule.runtime.api.exception.MuleRuntimeException;
import org.mule.runtime.api.tls.TlsContextFactory;
import org.mule.runtime.http.api.HttpService;
import org.mule.runtime.http.api.client.HttpClient;
import org.mule.runtime.http.api.client.HttpClientConfiguration;
import org.mule.runtime.http.api.client.HttpRequestOptions;
import org.mule.runtime.http.api.client.auth.HttpAuthentication;
import org.mule.runtime.http.api.client.ws.WebSocketCallback;
import org.mule.runtime.http.api.domain.message.request.HttpRequest;
import org.mule.runtime.http.api.domain.message.response.HttpResponse;
import org.mule.runtime.http.api.ws.WebSocket;
import org.mule.service.http.impl.service.HttpServiceImplementation;
import org.mule.tck.SimpleUnitTestSupportSchedulerService;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

import org.junit.rules.ExternalResource;

/**
 * Defines a {@link HttpClient} using a default implementation of {@link HttpService}
 *
 * <p/>
 * This rule is intended to simplify the usage of the {@link HttpClient} as it will be started/stopped as part of the test
 * lifecycle.
 */
public class TestHttpClient extends ExternalResource implements HttpClient {

  private final HttpService httpService;
  private TlsContextFactory tlsContextFactory;
  private HttpClient httpClient;

  private TestHttpClient() {
    this(new HttpServiceImplementation(new SimpleUnitTestSupportSchedulerService()));
  }

  private TestHttpClient(HttpService httpService) {
    checkArgument(httpService != null, "httpService cannot be null");
    this.httpService = httpService;
  }

  @Override
  protected void before() throws Throwable {
    HttpClientConfiguration.Builder builder = new HttpClientConfiguration.Builder();
    if (tlsContextFactory != null) {
      builder.setTlsContextFactory(tlsContextFactory);
    }
    HttpClientConfiguration configuration = builder.setName(getClass().getSimpleName()).build();
    httpClient = httpService.getClientFactory().create(configuration);
    httpClient.start();
  }

  @Override
  protected void after() {
    if (httpClient != null) {
      httpClient.stop();
    }
  }

  @Override
  public void start() {
    httpClient.start();
  }

  @Override
  public void stop() {
    httpClient.stop();
  }

  @Override
  public HttpResponse send(HttpRequest request, HttpRequestOptions options)
      throws IOException, TimeoutException {
    return httpClient.send(request,
                           isDebugging() ? HttpRequestOptions.builder(options).responseTimeout(MAX_VALUE).build() : options);
  }

  @Override
  public CompletableFuture<HttpResponse> sendAsync(HttpRequest request, HttpRequestOptions options) {
    return httpClient.sendAsync(request,
                                isDebugging() ? HttpRequestOptions.builder(options).responseTimeout(MAX_VALUE).build() : options);
  }

  @Override
  public CompletableFuture<WebSocket> openWebSocket(HttpRequest request,
                                                    HttpRequestOptions requestOptions,
                                                    String socketId,
                                                    WebSocketCallback callback) {
    return httpClient.openWebSocket(request, requestOptions, socketId, callback);
  }

  public static class Builder {

    private final HttpService service;
    private TlsContextFactory tlsContextFactory;

    /**
     * Creates a builder using a default {@link HttpService}
     */
    public Builder() {
      this.service = null;
    }

    /**
     * Creates a builder using a custom {@link HttpService}
     *
     * @param httpService httpService instance that will be used on the client. Non null
     */
    public Builder(HttpService httpService) {
      this.service = httpService;
    }

    /**
     * @param tlsContextFactory the TLS context factory for creating the context to secure the connection
     * @return same builder instance
     */
    public Builder tlsContextFactory(TlsContextFactory tlsContextFactory) {
      this.tlsContextFactory = tlsContextFactory;

      return this;
    }

    /**
     * @param tlsContextFactorySupplier a supplier for the TLS context factory for creating the context to secure the connection
     * @return same builder instance
     */
    public Builder tlsContextFactory(Supplier<TlsContextFactory> tlsContextFactorySupplier) {
      final TlsContextFactory tlsContextFactoryLocal = tlsContextFactorySupplier.get();
      try {
        initialiseIfNeeded(tlsContextFactoryLocal);
      } catch (Exception e) {
        throw new MuleRuntimeException(e);
      }

      this.tlsContextFactory = tlsContextFactoryLocal;

      return this;
    }

    /**
     * Builds the client
     *
     * @return a non null {@link TestHttpClient} with the provided configuration
     */
    public TestHttpClient build() {
      TestHttpClient httpClient;
      if (service == null) {
        httpClient = new TestHttpClient();
      } else {
        httpClient = new TestHttpClient(service);
      }

      httpClient.tlsContextFactory = tlsContextFactory;

      return httpClient;
    }
  }

  /**
   * Parses arguments passed to the runtime environment for debug flags
   * <p>
   * Options specified in:
   * <ul>
   * <li><a href="http://docs.oracle.com/javase/6/docs/technotes/guides/jpda/conninv.html#Invocation" >javase-6</a></li>
   * <li><a href="http://docs.oracle.com/javase/7/docs/technotes/guides/jpda/conninv.html#Invocation" >javase-7</a></li>
   * <li><a href="http://docs.oracle.com/javase/8/docs/technotes/guides/jpda/conninv.html#Invocation" >javase-8</a></li>
   *
   *
   * @return true if the current JVM was started in debug mode, false otherwise.
   */
  private static boolean isDebugging() {
    for (final String argument : ManagementFactory.getRuntimeMXBean().getInputArguments()) {
      if ("-Xdebug".equals(argument)) {
        return true;
      } else if (argument.startsWith("-agentlib:jdwp")) {
        return true;
      }
    }
    return false;
  }
}
