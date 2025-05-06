/*
 * Copyright 2023 Salesforce, Inc. All rights reserved.
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.service.http.impl.service.server.sse;

import static org.mule.service.http.impl.AllureConstants.HttpFeature.HTTP_SERVICE;
import static org.mule.service.http.impl.AllureConstants.HttpFeature.HttpStory.SSE;
import static org.mule.service.http.impl.AllureConstants.HttpFeature.HttpStory.SSE_ENDPOINT;
import static org.mule.service.http.impl.AllureConstants.HttpFeature.HttpStory.SSE_SOURCE;
import static org.mule.service.http.impl.util.sse.ServerSentEventTypeSafeMatcher.aServerSentEvent;

import static java.lang.String.format;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.fail;
import static org.junit.internal.matchers.ThrowableMessageMatcher.hasMessage;

import org.mule.runtime.http.api.client.HttpClient;
import org.mule.runtime.http.api.client.HttpClientConfiguration;
import org.mule.runtime.http.api.server.HttpServer;
import org.mule.runtime.http.api.server.HttpServerConfiguration;
import org.mule.runtime.http.api.server.ServerCreationException;
import org.mule.runtime.http.api.sse.client.SseRetryConfig;
import org.mule.runtime.http.api.sse.client.SseSource;
import org.mule.runtime.http.api.sse.client.SseSourceConfig;
import org.mule.service.http.impl.functional.AbstractHttpServiceTestCase;
import org.mule.service.http.impl.tck.ExecutorRule;
import org.mule.service.http.impl.util.sse.SSEEventsAggregator;
import org.mule.tck.junit4.rule.DynamicPort;

import io.qameta.allure.Feature;
import io.qameta.allure.Story;
import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

/**
 * Test for the communication between the SSE Source (client-side), and the SSE Endpoint (server-side).
 */
@Feature(HTTP_SERVICE)
@Story(SSE)
@Story(SSE_SOURCE)
@Story(SSE_ENDPOINT)
public class SseTestCase extends AbstractHttpServiceTestCase {

  @ClassRule
  public static ExecutorRule executorRule = new ExecutorRule();

  private static final SseRetryConfig DONT_RETRY_ON_EOS = new SseRetryConfig(true, 2000L, false);

  @Rule
  public DynamicPort serverPort = new DynamicPort("serverPort");

  private HttpServer httpServer;

  private HttpClient httpClient;
  private SseSourceConfig sseConfig;

  public SseTestCase(String serviceToLoad) {
    super(serviceToLoad);
  }

  @Before
  public void setUp() throws Exception {
    String sseUrl = format("http://localhost:%d/sse", serverPort.getNumber());
    sseConfig = SseSourceConfig.builder(sseUrl)
        .withRetryConfig(DONT_RETRY_ON_EOS)
        .build();
    httpServer = getHttpServer(serverPort.getNumber());
    httpServer.start();
    httpServer.sse("/sse", sseClient -> {
      try (sseClient) {
        for (int i = 0; i < 10; ++i) {
          sseClient.sendEvent("first", format("Event %d", i), "asd");
          sseClient.sendEvent("second", format("Event %d", i));
        }
      } catch (Exception e) {
        fail(e.getMessage());
      }
    });

    httpClient = service.getClientFactory().create(new HttpClientConfiguration.Builder()
        .setName("SSE Client")
        .setStreaming(true)
        .build());
    httpClient.start();
  }

  @After
  public void tearDown() {
    httpClient.stop();
    httpServer.stop().dispose();
  }

  @Test
  public void sseSourceNeedsTheClientStreamingEnabled() {
    HttpClient nonStreamingClient = service.getClientFactory().create(new HttpClientConfiguration.Builder()
        .setName("No-Streaming Client")
        .setStreaming(false)
        .build());
    var exception = assertThrows(IllegalStateException.class, () -> nonStreamingClient.sseSource(sseConfig));
    assertThat(exception, hasMessage(is("SSE source requires streaming enabled for client 'No-Streaming Client'")));
  }

  @Test
  public void allEventsToFallbackListener() throws InterruptedException {
    SSEEventsAggregator fallbackListener = new SSEEventsAggregator();

    SseSource sse = httpClient.sseSource(sseConfig);
    sse.register(fallbackListener);
    sse.open();

    assertThat(fallbackListener.getList(), contains(
                                                    aServerSentEvent("first", "Event 0"),
                                                    aServerSentEvent("second", "Event 0"),
                                                    aServerSentEvent("first", "Event 1"),
                                                    aServerSentEvent("second", "Event 1"),
                                                    aServerSentEvent("first", "Event 2"),
                                                    aServerSentEvent("second", "Event 2"),
                                                    aServerSentEvent("first", "Event 3"),
                                                    aServerSentEvent("second", "Event 3"),
                                                    aServerSentEvent("first", "Event 4"),
                                                    aServerSentEvent("second", "Event 4"),
                                                    aServerSentEvent("first", "Event 5"),
                                                    aServerSentEvent("second", "Event 5"),
                                                    aServerSentEvent("first", "Event 6"),
                                                    aServerSentEvent("second", "Event 6"),
                                                    aServerSentEvent("first", "Event 7"),
                                                    aServerSentEvent("second", "Event 7"),
                                                    aServerSentEvent("first", "Event 8"),
                                                    aServerSentEvent("second", "Event 8"),
                                                    aServerSentEvent("first", "Event 9"),
                                                    aServerSentEvent("second", "Event 9")));
  }

  @Test
  public void multiplexEventsByName() throws InterruptedException {
    SSEEventsAggregator firstListener = new SSEEventsAggregator();
    SSEEventsAggregator fallbackListener = new SSEEventsAggregator();

    SseSource sse = httpClient.sseSource(sseConfig);
    sse.register("first", firstListener);
    sse.register(fallbackListener);
    sse.open();

    assertThat(firstListener.getList(), contains(
                                                 aServerSentEvent("first", "Event 0"),
                                                 aServerSentEvent("first", "Event 1"),
                                                 aServerSentEvent("first", "Event 2"),
                                                 aServerSentEvent("first", "Event 3"),
                                                 aServerSentEvent("first", "Event 4"),
                                                 aServerSentEvent("first", "Event 5"),
                                                 aServerSentEvent("first", "Event 6"),
                                                 aServerSentEvent("first", "Event 7"),
                                                 aServerSentEvent("first", "Event 8"),
                                                 aServerSentEvent("first", "Event 9")));

    assertThat(fallbackListener.getList(), contains(
                                                    aServerSentEvent("second", "Event 0"),
                                                    aServerSentEvent("second", "Event 1"),
                                                    aServerSentEvent("second", "Event 2"),
                                                    aServerSentEvent("second", "Event 3"),
                                                    aServerSentEvent("second", "Event 4"),
                                                    aServerSentEvent("second", "Event 5"),
                                                    aServerSentEvent("second", "Event 6"),
                                                    aServerSentEvent("second", "Event 7"),
                                                    aServerSentEvent("second", "Event 8"),
                                                    aServerSentEvent("second", "Event 9")));
  }

  private HttpServer getHttpServer(int port) throws ServerCreationException {
    return service.getServerFactory().create(new HttpServerConfiguration.Builder()
        .setName("SSE Server")
        .setHost("localhost")
        .setPort(port)
        .build());
  }
}
