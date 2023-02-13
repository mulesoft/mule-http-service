/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.service.http.impl.service.client;

import static org.mule.runtime.api.i18n.I18nMessageFactory.createStaticMessage;
import static org.mule.runtime.api.util.DataUnit.KB;
import static org.mule.runtime.api.util.MuleSystemProperties.ENABLE_MULE_REDIRECT_PROPERTY;
import static org.mule.runtime.api.util.MuleSystemProperties.SYSTEM_PROPERTY_PREFIX;
import static org.mule.runtime.api.util.Preconditions.checkState;
import static org.mule.runtime.core.api.util.StringUtils.isEmpty;
import static org.mule.runtime.http.api.server.HttpServerProperties.PRESERVE_HEADER_CASE;

import static java.lang.Boolean.getBoolean;
import static java.lang.Boolean.parseBoolean;
import static java.lang.Integer.getInteger;
import static java.lang.Integer.parseInt;
import static java.lang.Math.max;
import static java.lang.Runtime.getRuntime;
import static java.lang.String.format;
import static java.lang.String.valueOf;
import static java.lang.System.getProperty;

import static com.ning.http.client.AsyncHttpClientConfigDefaults.defaultMaxRedirects;
import static com.ning.http.client.AsyncHttpClientConfigDefaults.defaultStrict302Handling;
import static com.ning.http.client.Realm.AuthScheme.NTLM;
import static com.ning.http.client.providers.grizzly.GrizzlyAsyncHttpProviderConfig.Property.DECOMPRESS_RESPONSE;
import static com.ning.http.client.providers.grizzly.GrizzlyAsyncHttpProviderConfig.Property.MAX_HTTP_PACKET_HEADER_SIZE;
import static com.ning.http.client.providers.grizzly.GrizzlyAsyncHttpProviderConfig.Property.TRANSPORT_CUSTOMIZER;
import static org.glassfish.grizzly.http.HttpCodecFilter.DEFAULT_MAX_HTTP_PACKET_HEADER_SIZE;
import static org.glassfish.grizzly.http.util.MimeHeaders.MAX_NUM_HEADERS_DEFAULT;

import org.mule.runtime.api.exception.MuleRuntimeException;
import org.mule.runtime.api.scheduler.Scheduler;
import org.mule.runtime.api.scheduler.SchedulerConfig;
import org.mule.runtime.api.scheduler.SchedulerService;
import org.mule.runtime.api.tls.TlsContextFactory;
import org.mule.runtime.api.tls.TlsContextTrustStoreConfiguration;
import org.mule.runtime.http.api.client.HttpClient;
import org.mule.runtime.http.api.client.HttpClientConfiguration;
import org.mule.runtime.http.api.client.HttpRequestOptions;
import org.mule.runtime.http.api.client.proxy.ProxyConfig;
import org.mule.runtime.http.api.domain.message.request.HttpRequest;
import org.mule.runtime.http.api.domain.message.response.HttpResponse;
import org.mule.runtime.http.api.tcp.TcpClientSocketProperties;
import org.mule.service.http.impl.service.client.async.PreservingClassLoaderAsyncHandler;
import org.mule.service.http.impl.service.client.async.ResponseAsyncHandler;
import org.mule.service.http.impl.service.client.async.ResponseBodyDeferringAsyncHandler;
import org.mule.service.http.impl.service.util.RedirectUtils;

import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import javax.net.ssl.SSLContext;

import com.ning.http.client.AsyncHandler;
import com.ning.http.client.AsyncHttpClient;
import com.ning.http.client.AsyncHttpClientConfig;
import com.ning.http.client.BodyDeferringAsyncHandler;
import com.ning.http.client.ListenableFuture;
import com.ning.http.client.MaxRedirectException;
import com.ning.http.client.ProxyServer;
import com.ning.http.client.Request;
import com.ning.http.client.RequestBuilder;
import com.ning.http.client.Response;
import com.ning.http.client.filter.FilterException;
import com.ning.http.client.providers.grizzly.GrizzlyAsyncHttpProvider;
import com.ning.http.client.providers.grizzly.GrizzlyAsyncHttpProviderConfig;
import com.ning.http.client.uri.Uri;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GrizzlyHttpClient implements HttpClient {

  private static final int DEFAULT_SELECTOR_THREAD_COUNT =
      getInteger(GrizzlyHttpClient.class.getName() + ".DEFAULT_SELECTOR_THREAD_COUNT",
                 Integer.max(getRuntime().availableProcessors(), 2));
  private static final int MAX_CONNECTION_LIFETIME = 30 * 60 * 1000;
  public static final String HOST_SEPARATOR = ",";
  private static final int DEFAULT_SEND_AND_DEFER_BUFFER_SIZE = KB.toBytes(10);
  private static final String DEFAULT_DECOMPRESS_PROPERTY_NAME = SYSTEM_PROPERTY_PREFIX + "http.client.decompress";

  private static final String ENABLE_REQUEST_STREAMING_PROPERTY_NAME = SYSTEM_PROPERTY_PREFIX + "http.requestStreaming.enable";
  private static boolean requestStreamingEnabled = parseBoolean(getProperty(ENABLE_REQUEST_STREAMING_PROPERTY_NAME, "true"));

  private static final int DEFAULT_REQUEST_STREAMING_BUFFER_SIZE = 8 * 1024;
  private static final String REQUEST_STREAMING_BUFFER_LEN_PROPERTY_NAME =
      SYSTEM_PROPERTY_PREFIX + "http.requestStreaming.bufferSize";
  private static int requestStreamingBufferSize =
      getInteger(REQUEST_STREAMING_BUFFER_LEN_PROPERTY_NAME, DEFAULT_REQUEST_STREAMING_BUFFER_SIZE);
  public static int MAX_REDIRECTS = defaultMaxRedirects();

  // Stream responses properties
  private static final String USE_WORKERS_FOR_STREAMING_PROPERTY_NAME =
      SYSTEM_PROPERTY_PREFIX + "http.responseStreaming.useWorkers";
  private static final boolean useWorkersForStreaming =
      parseBoolean(getProperty(USE_WORKERS_FOR_STREAMING_PROPERTY_NAME, "true"));
  private static final String MAX_STREAMING_WORKERS_PROPERTY_NAME = SYSTEM_PROPERTY_PREFIX + "http.responseStreaming.maxWorkers";
  private static int maxStreamingWorkers = parseInt(getProperty(MAX_STREAMING_WORKERS_PROPERTY_NAME, "-1"));
  private static final String STREAMING_WORKERS_QUEUE_SIZE_PROPERTY_NAME =
      SYSTEM_PROPERTY_PREFIX + "http.responseStreaming.queueSize";
  private static int streamingWorkersQueueSize = parseInt(getProperty(STREAMING_WORKERS_QUEUE_SIZE_PROPERTY_NAME, "-1"));
  private static final int DEFAULT_STREAMING_WORKERS_QUEUE_SIZE = getDefaultStreamingWorkersQueueSize();

  public static final String CUSTOM_MAX_HTTP_PACKET_HEADER_SIZE = SYSTEM_PROPERTY_PREFIX + "http.client.headerSectionSize";

  private static boolean enableMuleRedirect = parseBoolean(getProperty(ENABLE_MULE_REDIRECT_PROPERTY, "true"));

  private static final String MAX_CLIENT_REQUEST_HEADERS_KEY = SYSTEM_PROPERTY_PREFIX + "http.MAX_CLIENT_REQUEST_HEADERS";
  private static int MAX_CLIENT_REQUEST_HEADERS =
      getInteger(MAX_CLIENT_REQUEST_HEADERS_KEY, MAX_NUM_HEADERS_DEFAULT);

  private static final Logger LOGGER = LoggerFactory.getLogger(GrizzlyHttpClient.class);

  private static boolean DEFAULT_DECOMPRESS = getBoolean(DEFAULT_DECOMPRESS_PROPERTY_NAME);

  private final TlsContextFactory tlsContextFactory;
  private final ProxyConfig proxyConfig;
  private final TcpClientSocketProperties clientSocketProperties;
  private final int maxConnections;
  private final boolean usePersistentConnections;
  private final int connectionIdleTimeout;
  private final boolean streamingEnabled;

  private final int responseBufferSize;
  private final String name;
  private final boolean decompressionEnabled;
  private Scheduler selectorScheduler;
  private Scheduler workerScheduler;
  private final SchedulerService schedulerService;
  private final SchedulerConfig schedulersConfig;
  protected AsyncHttpClient asyncHttpClient;
  private SSLContext sslContext;

  private final HttpResponseCreator httpResponseCreator = new HttpResponseCreator();

  private final TlsContextFactory defaultTlsContextFactory = TlsContextFactory.builder().buildDefault();

  private final RedirectUtils redirectUtils;
  private static final boolean isStrict302Handling = defaultStrict302Handling();
  private final RequestHeaderPopulator headerPopulator;

  public GrizzlyHttpClient(HttpClientConfiguration config, SchedulerService schedulerService, SchedulerConfig schedulersConfig) {
    this.tlsContextFactory = config.getTlsContextFactory();
    this.proxyConfig = config.getProxyConfig();
    this.clientSocketProperties = config.getClientSocketProperties();
    this.maxConnections = config.getMaxConnections();
    this.usePersistentConnections = config.isUsePersistentConnections();
    this.connectionIdleTimeout = config.getConnectionIdleTimeout();
    this.streamingEnabled = config.isStreaming();
    this.responseBufferSize = config.getResponseBufferSize();
    this.decompressionEnabled = config.isDecompress() != null ? config.isDecompress() : DEFAULT_DECOMPRESS;
    this.name = config.getName();

    this.schedulerService = schedulerService;
    this.schedulersConfig = schedulersConfig;
    this.redirectUtils = new RedirectUtils(isStrict302Handling, PRESERVE_HEADER_CASE);
    this.headerPopulator = new RequestHeaderPopulator(usePersistentConnections);
  }

  @Override
  public void start() {
    selectorScheduler = schedulerService.customScheduler(schedulersConfig
        .withDirectRunCpuLightWhenTargetBusy(true)
        .withMaxConcurrentTasks(DEFAULT_SELECTOR_THREAD_COUNT)
        .withName(name), DEFAULT_SELECTOR_THREAD_COUNT);
    workerScheduler = getWorkerScheduler(schedulersConfig.withName(name + ".requester.workers"));

    AsyncHttpClientConfig.Builder builder = new AsyncHttpClientConfig.Builder();
    builder.setAllowPoolingConnections(true);
    builder.setMaxRequestHeaders(MAX_CLIENT_REQUEST_HEADERS);

    configureTransport(builder);
    configureTlsContext(builder);
    configureProxy(builder);
    configureConnections(builder);

    AsyncHttpClientConfig config = builder.build();
    asyncHttpClient = new AsyncHttpClient(new GrizzlyAsyncHttpProvider(config), config);
  }

  private Scheduler getWorkerScheduler(SchedulerConfig config) {
    if (streamingEnabled && useWorkersForStreaming) {
      // TODO MULE-19084: investigate how many schedulers/threads may be created here on complex apps with lots of
      // requester-configs.
      return schedulerService.customScheduler(config.withMaxConcurrentTasks(getMaxStreamingWorkers()),
                                              getStreamingWorkersQueueSize());
    } else {
      return schedulerService.ioScheduler(config);
    }
  }

  private int getMaxStreamingWorkers() {
    return maxStreamingWorkers > 0 ? maxStreamingWorkers : DEFAULT_SELECTOR_THREAD_COUNT * 4;
  }

  private int getStreamingWorkersQueueSize() {
    return streamingWorkersQueueSize > 0 ? streamingWorkersQueueSize : DEFAULT_STREAMING_WORKERS_QUEUE_SIZE;
  }

  // This default has been extracted from
  // org.mule.service.scheduler.internal.config.ContainerThreadPoolsConfig.BIG_POOL_DEFAULT_SIZE.
  private static int getDefaultStreamingWorkersQueueSize() {
    int cores = getRuntime().availableProcessors();
    long memoryInKB = getRuntime().maxMemory() / 1024;
    return (int) max(2, cores + ((memoryInKB - 245760) / 5120));
  }

  private void configureTlsContext(AsyncHttpClientConfig.Builder builder) {
    TlsContextFactory resolvedTlsContextFactory;
    if (tlsContextFactory != null) {
      resolvedTlsContextFactory = tlsContextFactory;
      try {
        sslContext = tlsContextFactory.createSslContext();
      } catch (Exception e) {
        throw new MuleRuntimeException(createStaticMessage("Cannot initialize SSL context"), e);
      }

      // This sets all the TLS configuration needed, except for the enabled protocols and cipher suites.
      builder.setSSLContext(sslContext);

      TlsContextTrustStoreConfiguration trustStoreConfiguration = tlsContextFactory.getTrustStoreConfiguration();

      if (trustStoreConfiguration != null && trustStoreConfiguration.isInsecure()) {
        LOGGER
            .warn(format("TLS configuration for client %s has been set to use an insecure trust store. This means no certificate validations will be performed, rendering connections vulnerable to attacks. Use at own risk.",
                         name));
        // This disables hostname verification
        builder.setAcceptAnyCertificate(true);
      }
    } else {
      resolvedTlsContextFactory = defaultTlsContextFactory;
    }
    // These complete the set up, they must always be set in case an implicit SSL connection is used
    if (resolvedTlsContextFactory.getEnabledCipherSuites() != null) {
      builder.setEnabledCipherSuites(resolvedTlsContextFactory.getEnabledCipherSuites());
    }
    if (resolvedTlsContextFactory.getEnabledProtocols() != null) {
      builder.setEnabledProtocols(resolvedTlsContextFactory.getEnabledProtocols());
    }
  }

  private void configureProxy(AsyncHttpClientConfig.Builder builder) {
    if (proxyConfig != null) {
      doConfigureProxy(builder, proxyConfig);
    }
  }

  protected void doConfigureProxy(AsyncHttpClientConfig.Builder builder, ProxyConfig proxyConfig) {
    builder.setProxyServer(buildProxy(proxyConfig));
  }

  ProxyServer buildProxy(ProxyConfig proxyConfig) {
    ProxyServer proxyServer;
    if (!isEmpty(proxyConfig.getUsername())) {
      proxyServer =
          new ProxyServer(proxyConfig.getHost(), proxyConfig.getPort(), proxyConfig.getUsername(), proxyConfig.getPassword());
      if (proxyConfig instanceof ProxyConfig.NtlmProxyConfig) {
        proxyServer.setNtlmDomain(((ProxyConfig.NtlmProxyConfig) proxyConfig).getNtlmDomain());
        try {
          proxyServer.setNtlmHost(getHostName());
        } catch (UnknownHostException e) {
          // do nothing, let the default behaviour be used
        }
        proxyServer.setScheme(NTLM);
      }
    } else {
      proxyServer = new ProxyServer(proxyConfig.getHost(), proxyConfig.getPort());
    }
    if (proxyConfig.getNonProxyHosts() != null && !proxyConfig.getNonProxyHosts().isEmpty()) {
      for (final String host : proxyConfig.getNonProxyHosts().split(HOST_SEPARATOR)) {
        proxyServer.addNonProxyHost(host.trim());
      }
    }
    return proxyServer;
  }

  private void configureTransport(AsyncHttpClientConfig.Builder builder) {
    GrizzlyAsyncHttpProviderConfig providerConfig = new GrizzlyAsyncHttpProviderConfig();
    boolean tlsEnabled = tlsContextFactory != null;
    CompositeTransportCustomizer compositeTransportCustomizer = new CompositeTransportCustomizer();
    compositeTransportCustomizer
        .addTransportCustomizer(new IOStrategyTransportCustomizer(selectorScheduler, workerScheduler, streamingEnabled,
                                                                  DEFAULT_SELECTOR_THREAD_COUNT, tlsEnabled));
    compositeTransportCustomizer.addTransportCustomizer(new LoggerTransportCustomizer(name));

    if (clientSocketProperties != null) {
      compositeTransportCustomizer.addTransportCustomizer(new SocketConfigTransportCustomizer(clientSocketProperties));
      builder.setConnectTimeout(clientSocketProperties.getConnectionTimeout());
    }

    providerConfig.addProperty(TRANSPORT_CUSTOMIZER, compositeTransportCustomizer);
    providerConfig.addProperty(DECOMPRESS_RESPONSE, this.decompressionEnabled);
    providerConfig.addProperty(MAX_HTTP_PACKET_HEADER_SIZE, retrieveMaximumHeaderSectionSize());
    builder.setAsyncHttpClientProviderConfig(providerConfig);
  }

  private void configureConnections(AsyncHttpClientConfig.Builder builder) {
    if (maxConnections > 0) {
      builder.addRequestFilter(new CustomTimeoutThrottleRequestFilter(maxConnections));
    }

    builder.setMaxConnections(maxConnections);
    builder.setMaxConnectionsPerHost(maxConnections);

    builder.setAllowPoolingConnections(usePersistentConnections);
    builder.setAllowPoolingSslConnections(usePersistentConnections);

    builder.setConnectionTTL(MAX_CONNECTION_LIFETIME);
    builder.setPooledConnectionIdleTimeout(connectionIdleTimeout);

    builder.setIOThreadMultiplier(1);
  }

  @Override
  public HttpResponse send(HttpRequest request, HttpRequestOptions options) throws IOException, TimeoutException {
    checkState(asyncHttpClient != null, "The client must be started before use.");
    if (streamingEnabled) {
      return sendAndDefer(request, createGrizzlyRequest(request, options), options, 0);
    } else {
      return sendAndWait(request, createGrizzlyRequest(request, options), options, 0);
    }
  }

  /**
   * Blocking send which uses a {@link PipedOutputStream} to populate the HTTP response as it arrives and propagates a
   * {@link PipedInputStream} as soon as the response headers are parsed.
   * <p/>
   * Because of the internal buffer used to hold the arriving chunks, the response MUST be eventually read or the worker threads
   * will block waiting to allocate them. Likewise, read/write speed differences could cause issues. The buffer size can be
   * customized for these reason.
   */
  private HttpResponse sendAndDefer(HttpRequest request, Request grizzlyRequest, HttpRequestOptions options, int currentRedirects)
      throws IOException, TimeoutException {
    PipedOutputStream outPipe = new PipedOutputStream();
    PipedInputStream inPipe =
        new PipedInputStream(outPipe, responseBufferSize > 0 ? responseBufferSize : DEFAULT_SEND_AND_DEFER_BUFFER_SIZE);
    BodyDeferringAsyncHandler asyncHandler = new BodyDeferringAsyncHandler(outPipe);
    asyncHttpClient.executeRequest(grizzlyRequest, asyncHandler);
    try {
      Response response = asyncHandler.getResponse();
      HttpResponse httpResponse = httpResponseCreator.create(response, inPipe);
      if (redirectUtils.shouldFollowRedirect(httpResponse, options, enableMuleRedirect)) {
        if (currentRedirects >= MAX_REDIRECTS) {
          throw new IOException("Max redirects exceeded", new MaxRedirectException());
        }
        HttpRequest redirectRequest = redirectUtils.createRedirectRequest(httpResponse, request, options);
        httpResponse = sendAndDefer(redirectRequest,
                                    createGrizzlyRedirectRequest(redirectRequest, httpResponse, options), options,
                                    currentRedirects + 1);
      }
      return httpResponse;
    } catch (IOException e) {
      if (e.getCause() instanceof TimeoutException) {
        throw (TimeoutException) e.getCause();
      } else if (e.getCause() instanceof IOException) {
        throw (IOException) e.getCause();
      } else {
        throw new IOException(e);
      }
    } catch (InterruptedException e) {
      throw new IOException(e);
    }
  }

  /**
   * Blocking send which waits to load the whole response to memory before propagating it.
   */
  private HttpResponse sendAndWait(HttpRequest request, Request grizzlyRequest, HttpRequestOptions options, int currentRedirects)
      throws IOException, TimeoutException {
    ListenableFuture<Response> future = asyncHttpClient.executeRequest(grizzlyRequest);
    try {
      // No timeout is used to get the value of the future object, as the responseTimeout configured in the request that
      // is being sent will make the call throw a {@code TimeoutException} if this time is exceeded.
      Response response = future.get();

      // Under high load, sometimes the get() method returns null. Retrying once fixes the problem (see MULE-8712).
      if (response == null) {
        if (LOGGER.isDebugEnabled()) {
          LOGGER.debug("Null response returned by async client");
        }
        response = future.get();
      }
      HttpResponse httpResponse = httpResponseCreator.create(response, response.getResponseBodyAsStream());
      if (redirectUtils.shouldFollowRedirect(httpResponse, options, enableMuleRedirect)) {
        if (currentRedirects >= MAX_REDIRECTS) {
          throw new IOException("Max redirects exceeded", new MaxRedirectException());
        }
        HttpRequest redirectRequest = redirectUtils.createRedirectRequest(httpResponse, request, options);
        httpResponse = sendAndWait(redirectRequest,
                                   createGrizzlyRedirectRequest(redirectRequest, httpResponse, options), options,
                                   currentRedirects + 1);
      }
      return httpResponse;
    } catch (InterruptedException e) {
      throw new IOException(e.getMessage(), e);
    } catch (ExecutionException e) {
      if (e.getCause() instanceof TimeoutException) {
        throw (TimeoutException) e.getCause();
      } else if (e.getCause() instanceof IOException) {
        throw (IOException) e.getCause();
      } else if (e.getCause() instanceof FilterException) {
        throw new IOException(e.getCause().getMessage(), e.getCause());
      } else {
        throw new IOException(e.getMessage(), e);
      }
    }
  }

  @Override
  public CompletableFuture<HttpResponse> sendAsync(HttpRequest request, HttpRequestOptions options) {
    checkState(asyncHttpClient != null, "The client must be started before use.");
    try {
      return sendAsync(request, createGrizzlyRequest(request, options), options, 0);
    } catch (Throwable e) {
      CompletableFuture ex = new CompletableFuture();
      ex.completeExceptionally(e);
      return ex;
    }
  }

  private CompletableFuture<HttpResponse> sendAsync(HttpRequest request, Request grizzlyRequest,
                                                    HttpRequestOptions options, int currentRedirects) {
    CompletableFuture<HttpResponse> future = new CompletableFuture<>();
    try {
      AsyncHandler<Response> asyncHandler;

      CompletableFuture<HttpResponse> auxFuture = new CompletableFuture<>();
      if (streamingEnabled) {
        asyncHandler =
            new PreservingClassLoaderAsyncHandler<>(new ResponseBodyDeferringAsyncHandler(auxFuture, responseBufferSize));
      } else {
        asyncHandler = new PreservingClassLoaderAsyncHandler<>(new ResponseAsyncHandler(auxFuture));
      }

      auxFuture.whenComplete((response, exception) -> {
        if (response != null) {
          if (redirectUtils.shouldFollowRedirect(response, options, enableMuleRedirect)) {
            try {
              handleRedirectAsync(request, response, options, currentRedirects, future);
            } catch (Throwable e) {
              future.completeExceptionally(e);
            }
          } else {
            future.complete(response);
          }
        } else {
          future.completeExceptionally(exception);
        }
      });
      asyncHttpClient.executeRequest(grizzlyRequest, asyncHandler);
    } catch (Exception e) {
      future.completeExceptionally(e);
    }
    return future;
  }

  private void handleRedirectAsync(HttpRequest request, HttpResponse response, HttpRequestOptions options,
                                   int currentRedirects, CompletableFuture<HttpResponse> future)
      throws IOException {
    if (currentRedirects >= MAX_REDIRECTS) {
      future.completeExceptionally(new MaxRedirectException());
      return;
    }

    HttpRequest redirectRequest = redirectUtils.createRedirectRequest(response, request, options);
    Request grizzlyRequest = createGrizzlyRedirectRequest(redirectRequest, response, options);
    sendAsync(redirectRequest, grizzlyRequest, options, currentRedirects + 1)
        .whenComplete((redirectResponse, redirectException) -> {
          if (redirectResponse != null) {
            future.complete(redirectResponse);
          } else {
            future.completeExceptionally(redirectException);
          }
        });
  }

  // In case we receive a 30x response, this method will create a new Grizzly request adding also the cookies present
  // in the set-cookies header in the response.
  private Request createGrizzlyRedirectRequest(HttpRequest request, HttpResponse response, HttpRequestOptions options)
      throws IOException {
    RequestBuilder reqBuilder = createGrizzlyRequestBuilder(request, options);
    redirectUtils.handleResponseCookies(reqBuilder, response);
    return reqBuilder.build();
  }

  protected Request createGrizzlyRequest(HttpRequest request, HttpRequestOptions options)
      throws IOException {
    return createGrizzlyRequestBuilder(request, options).build();
  }

  private RequestBuilder createGrizzlyRequestBuilder(HttpRequest request, HttpRequestOptions options)
      throws IOException {
    RequestBuilder reqBuilder =
        createRequestBuilder(request, options,
                             new GrizzlyRequestConfigurer(this, options, request, enableMuleRedirect,
                                                          requestStreamingEnabled, requestStreamingBufferSize));
    URI uri = request.getUri();
    if (uri.getScheme() == null) {
      throw new IllegalArgumentException("The uri provided '" + uri + "' must contain a scheme.");
    }
    if (uri.getHost() == null) {
      throw new IllegalArgumentException("The uri provided '" + uri + "' must contain a host.");
    }
    reqBuilder.setUri(new Uri(uri.getScheme(), uri.getRawUserInfo(), uri.getHost(), uri.getPort(), uri.getRawPath(),
                              uri.getRawQuery() != null ? uri.getRawQuery() + (request.getQueryParams().isEmpty() ? "" : "&")
                                  : null));
    return reqBuilder;
  }

  protected RequestBuilder createRequestBuilder(HttpRequest request, HttpRequestOptions options,
                                                RequestConfigurer requestConfigurer)
      throws IOException {
    // url strings must already be properly encoded
    final RequestBuilder requestBuilder = new RequestBuilder(request.getMethod(), true);
    requestConfigurer.configure(requestBuilder);
    return requestBuilder;
  }

  protected void populateHeaders(HttpRequest request, RequestBuilder builder) {
    headerPopulator.populateHeaders(request, builder);
  }

  @FunctionalInterface
  protected interface RequestConfigurer {

    void configure(RequestBuilder reqBuilder) throws IOException;
  }

  String getHostName() throws UnknownHostException {
    return InetAddress.getLocalHost().getHostName();
  }

  protected ProxyConfig getProxyConfig() {
    return proxyConfig;
  }

  @Override
  public void stop() {
    asyncHttpClient.close();
    workerScheduler.stop();
    selectorScheduler.stop();
  }

  public static void refreshSystemProperties() {
    DEFAULT_DECOMPRESS = getBoolean(DEFAULT_DECOMPRESS_PROPERTY_NAME);
    MAX_CLIENT_REQUEST_HEADERS = getInteger(MAX_CLIENT_REQUEST_HEADERS_KEY, MAX_NUM_HEADERS_DEFAULT);
    enableMuleRedirect = parseBoolean(getProperty(ENABLE_MULE_REDIRECT_PROPERTY, "true"));
  }

  private int retrieveMaximumHeaderSectionSize() {
    try {
      return Integer
          .valueOf(getProperty(CUSTOM_MAX_HTTP_PACKET_HEADER_SIZE, valueOf(DEFAULT_MAX_HTTP_PACKET_HEADER_SIZE)));
    } catch (NumberFormatException e) {
      throw new MuleRuntimeException(createStaticMessage(format("Invalid value '%s' for '%s' configuration.",
                                                                getProperty(CUSTOM_MAX_HTTP_PACKET_HEADER_SIZE),
                                                                CUSTOM_MAX_HTTP_PACKET_HEADER_SIZE)),
                                     e);
    }
  }
}
