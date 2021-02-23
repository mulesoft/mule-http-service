/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.service.http.impl.functional.server;

import static java.lang.String.valueOf;
import static java.lang.Thread.sleep;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.mule.runtime.core.api.config.MuleProperties.MULE_HOME_DIRECTORY_PROPERTY;
import static org.mule.runtime.http.api.HttpConstants.HttpStatus.OK;
import static org.mule.service.http.impl.config.ContainerTcpServerSocketProperties.PROPERTY_PREFIX;
import org.mule.runtime.api.util.Reference;
import org.mule.runtime.http.api.client.HttpClient;
import org.mule.runtime.http.api.client.HttpClientConfiguration;
import org.mule.runtime.http.api.domain.entity.ByteArrayHttpEntity;
import org.mule.runtime.http.api.domain.message.request.HttpRequest;
import org.mule.runtime.http.api.domain.message.response.HttpResponse;
import org.mule.runtime.http.api.server.HttpServerConfiguration;
import org.mule.service.http.impl.functional.ResponseReceivedProbe;
import org.mule.tck.junit4.rule.SystemProperty;
import org.mule.tck.probe.JUnitLambdaProbe;
import org.mule.tck.probe.PollingProber;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.Writer;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/**
 * This test validates that a server response can be delayed as long as needed by deferring its body writing. As it is tested, it
 * also validates that a streaming enabled client can handle such response by first analyzing its metadata and then awaiting for
 * the body parts.
 */
public class HttpServerDelayedResponseTestCase extends AbstractHttpServerTestCase {

  private static final String INITIAL_CHUNK = ":";
  private static final int CONNECTION_TIMEOUT_MILLIS = 500;
  private static final int PROBE_TIMEOUT_MILLIS1 = 2000;
  private static final int POLL_DELAY_MILLIS = 200;

  @ClassRule
  public static TemporaryFolder confDir = new TemporaryFolder();

  @Rule
  public SystemProperty muleHome = new SystemProperty(MULE_HOME_DIRECTORY_PROPERTY, getMuleHome());

  private PollingProber pollingProber = new PollingProber(PROBE_TIMEOUT_MILLIS1, POLL_DELAY_MILLIS);
  private Writer writer;
  private HttpClient client;

  public HttpServerDelayedResponseTestCase(String serviceToLoad) {
    super(serviceToLoad);
  }

  @BeforeClass
  public static void createHttpPropertiesFile() throws Exception {
    PrintWriter writer = new PrintWriter(getHttpPropertiesFile(), "UTF-8");
    writer.println(PROPERTY_PREFIX + "serverTimeout=" + CONNECTION_TIMEOUT_MILLIS);
    writer.close();
  }

  @AfterClass
  public static void removeHttpPropertiesFile() {
    if (!getHttpPropertiesFile().delete()) {
      throw new IllegalStateException("Couldn't delete properties file");
    }
  }

  private static File getHttpPropertiesFile() {
    String path = getMuleHome();
    File conf = new File(path, "conf");
    if (!conf.mkdir()) {
      throw new IllegalStateException("Couldn't create 'conf' directory");
    }
    return new File(conf.getPath(), "http-server-sockets.conf");
  }

  private static String getMuleHome() {
    return confDir.getRoot().getAbsolutePath();
  }

  @Before
  public void setUp() throws Exception {
    setUpServer();
    server.addRequestHandler("/test", (request, callback) -> {
      writer = callback.startResponse(HttpResponse.builder()
          .entity(new ByteArrayHttpEntity("ignored".getBytes()))
          .build(),
                                      new IgnoreResponseStatusCallback(),
                                      UTF_8);
      // Send dummy data to force the HTTP client to provide a response
      try {
        sendBodyPart(INITIAL_CHUNK);
      } catch (IOException e) {
        // Do nothing
      }
    });
    client = service.getClientFactory().create(new HttpClientConfiguration.Builder()
        .setName("delayed-response-test-client")
        .setStreaming(true)
        .build());
    client.start();
  }

  @Override
  protected HttpServerConfiguration.Builder configureServer(HttpServerConfiguration.Builder builder) {
    return builder.setConnectionIdleTimeout(CONNECTION_TIMEOUT_MILLIS);
  }

  @Override
  protected String getServerName() {
    return "delayed-response-test";
  }

  @After
  public void stopClient() {
    if (client != null) {
      client.stop();
    }
  }

  @Test
  public void connectionRemainsOpenUntilWriterCloses() throws Exception {
    final Reference<HttpResponse> responseReference = new Reference<>();
    client.sendAsync(getRequest()).whenComplete((response, exception) -> responseReference.set(response));

    pollingProber.check(new ResponseReceivedProbe(responseReference));

    HttpResponse response = responseReference.get();
    assertThat(response.getStatusCode(), is(OK.getStatusCode()));

    BufferedReader reader = new BufferedReader(new InputStreamReader(response.getEntity().getContent()));
    assertBodyPart(INITIAL_CHUNK, reader);

    String firstChunk = "first chunk of real data";
    sendBodyPart(firstChunk);
    assertBodyPart(firstChunk, reader);

    sleep(CONNECTION_TIMEOUT_MILLIS * 3);

    String secondChunk = "second chunk of real data";
    sendBodyPart(secondChunk);
    assertBodyPart(secondChunk, reader);

    writer.close();

    pollingProber.check(new JUnitLambdaProbe(() -> reader.readLine() == null));
  }

  private HttpRequest getRequest() {
    return HttpRequest.builder().uri("http://localhost:" + port.getValue() + "/test").build();
  }

  private void sendBodyPart(String part) throws IOException {
    writer.write(part);
    writer.write("\n");
    writer.flush();
  }

  private void assertBodyPart(String expected, BufferedReader reader) throws IOException {
    assertThat(reader.readLine(), is(expected));
  }

}
