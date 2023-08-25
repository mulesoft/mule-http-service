/*
 * Copyright 2023 Salesforce, Inc. All rights reserved.
 */
package org.mule.service.http.impl.functional.server;

import static java.lang.String.valueOf;
import static java.lang.Thread.sleep;
import static java.util.Collections.singletonList;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.mule.runtime.api.metadata.MediaType.TEXT;
import static org.mule.runtime.core.api.util.StringUtils.isEmpty;
import static org.mule.runtime.http.api.HttpConstants.Method.GET;
import static org.mule.runtime.http.api.HttpHeaders.Names.CONTENT_TYPE;
import static org.mule.service.http.impl.config.ContainerTcpServerSocketProperties.PROPERTY_PREFIX;
import static org.mule.service.http.impl.config.ContainerTcpServerSocketProperties.SERVER_SOCKETS_FILE;
import org.mule.runtime.http.api.domain.entity.ByteArrayHttpEntity;
import org.mule.runtime.http.api.domain.message.response.HttpResponse;
import org.mule.runtime.http.api.server.HttpServer;
import org.mule.runtime.http.api.server.HttpServerConfiguration;
import org.mule.runtime.http.api.server.ServerCreationException;
import org.mule.service.http.impl.functional.AbstractHttpServiceTestCase;
import org.mule.tck.junit4.rule.DynamicPort;
import org.mule.tck.junit4.rule.SystemProperty;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.Socket;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class HttpServerTimeoutTestCase extends AbstractHttpServiceTestCase {

  private static int SERVER_TIMEOUT_MILLIS = 500;
  private static int CONNECTION_TIMEOUT_MILLIS = 2000;

  @ClassRule
  public static TemporaryFolder temporaryFolder = new TemporaryFolder();

  @Rule
  public DynamicPort port1 = new DynamicPort("port1");
  @Rule
  public DynamicPort port2 = new DynamicPort("port2");
  @Rule
  public DynamicPort port3 = new DynamicPort("port3");
  @Rule
  public SystemProperty serverTimeout = new SystemProperty(SERVER_SOCKETS_FILE, getHttpPropertiesFile().getAbsolutePath());

  private HttpServer server1;
  private HttpServer server2;
  private HttpServer server3;

  public HttpServerTimeoutTestCase(String serviceToLoad) {
    super(serviceToLoad);
  }

  @BeforeClass
  public static void createHttpPropertiesFile() throws Exception {
    PrintWriter writer = new PrintWriter(getHttpPropertiesFile(), "UTF-8");
    writer.println(PROPERTY_PREFIX + "serverTimeout=" + valueOf(SERVER_TIMEOUT_MILLIS));
    writer.close();
  }

  @AfterClass
  public static void removeHttpPropertiesFile() {
    getHttpPropertiesFile().delete();
  }

  private static File getHttpPropertiesFile() {
    String path = temporaryFolder.getRoot().getAbsolutePath();
    return new File(path, "custom-http-server-sockets.conf");
  }

  @Before
  public void setUp() throws Exception {
    server1 = buildServer(getServerBuilder(port1, "server-timeout-test").setUsePersistentConnections(false));
    server2 = buildServer(getServerBuilder(port2, "server-connection-timeout-test")
        .setConnectionIdleTimeout(CONNECTION_TIMEOUT_MILLIS));
    server3 = buildServer(getServerBuilder(port3, "server-no-timeout-test").setConnectionIdleTimeout(-1));
  }

  @After
  public void tearDown() {
    close(server1);
    close(server2);
    close(server3);
  }

  private HttpServer buildServer(HttpServerConfiguration.Builder serverBuilder) throws ServerCreationException, IOException {
    HttpServer httpServer = service.getServerFactory().create(serverBuilder.build());
    httpServer.start();
    httpServer.addRequestHandler(singletonList(GET.name()), "/test", (requestContext, responseCallback) -> {
      responseCallback.responseReady(HttpResponse.builder().entity(new ByteArrayHttpEntity("Success!".getBytes()))
          .addHeader(CONTENT_TYPE, TEXT.toRfcString())
          .build(), new IgnoreResponseStatusCallback());

    });
    return httpServer;
  }

  private HttpServerConfiguration.Builder getServerBuilder(DynamicPort port, String name) {
    return new HttpServerConfiguration.Builder()
        .setHost("localhost")
        .setPort(port.getNumber())
        .setName(name);
  }

  private void close(HttpServer server) {
    if (server != null) {
      server.stop();
      server.dispose();
    }
  }

  @Test
  public void serverTimeoutsTcpConnection() throws Exception {
    Socket socket = new Socket("localhost", port1.getNumber());
    sleep(SERVER_TIMEOUT_MILLIS * 3);
    sendRequest(socket);
    assertThat(getResponse(socket), is(nullValue()));
  }

  @Test
  public void keepAlivePreventsServerTimeout() throws Exception {
    Socket socket = new Socket("localhost", port2.getNumber());
    sendRequest(socket);
    assertThat(getResponse(socket), is(notNullValue()));
    sleep(SERVER_TIMEOUT_MILLIS * 3);
    sendRequest(socket);
    assertThat(getResponse(socket), is(notNullValue()));
    sleep(CONNECTION_TIMEOUT_MILLIS + SERVER_TIMEOUT_MILLIS * 3);
    sendRequest(socket);
    assertThat(getResponse(socket), is(nullValue()));
  }

  @Test
  public void infiniteKeepAlivePreventsServerTimeout() throws Exception {
    Socket socket = new Socket("localhost", port3.getNumber());
    sendRequest(socket);
    assertThat(getResponse(socket), is(notNullValue()));
    sleep(SERVER_TIMEOUT_MILLIS * 3);
    sendRequest(socket);
    assertThat(getResponse(socket), is(notNullValue()));
  }

  private void sendRequest(Socket socket) throws IOException {
    PrintWriter writer = new PrintWriter(socket.getOutputStream());
    writer.println("GET /test HTTP/1.1");
    writer.println("Host: www.example.com");
    writer.println("");
    writer.flush();
  }

  private String getResponse(Socket socket) {
    try {
      StringWriter writer = new StringWriter();
      BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
      if (reader != null) {
        String line;
        while (!isEmpty(line = reader.readLine())) {
          writer.append(line).append("\r\n");
        }
      }
      String response = writer.toString();
      return response.length() == 0 ? null : response;
    } catch (IOException e) {
      return null;
    }
  }

}
