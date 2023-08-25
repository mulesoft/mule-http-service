/*
 * Copyright 2023 Salesforce, Inc. All rights reserved.
 */
package org.mule.service.http.impl.service.server;

import org.apache.http.StatusLine;
import org.apache.http.client.fluent.Request;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mule.runtime.core.api.util.IOUtils;
import org.mule.runtime.http.api.HttpConstants;
import org.mule.runtime.http.api.domain.entity.ByteArrayHttpEntity;
import org.mule.runtime.http.api.domain.message.response.HttpResponse;
import org.mule.service.http.impl.functional.server.AbstractHttpServerTestCase;
import org.mule.tck.junit4.rule.SystemProperty;

import java.io.IOException;
import java.util.Optional;

import static java.lang.String.format;
import static java.lang.System.clearProperty;
import static java.lang.System.setProperty;
import static java.util.Collections.singletonList;
import static java.util.Optional.empty;
import static java.util.Optional.of;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.mule.runtime.api.metadata.MediaType.TEXT;
import static org.mule.runtime.http.api.HttpConstants.HttpStatus.OK;
import static org.mule.runtime.http.api.HttpConstants.HttpStatus.NOT_FOUND;
import static org.mule.runtime.http.api.HttpConstants.Method.GET;
import static org.mule.runtime.http.api.HttpHeaders.Names.CONTENT_TYPE;
import static org.mule.service.http.impl.service.util.DefaultRequestMatcherRegistry.HTTP_SERVICE_ENCODED_SLASH_ENABLED_PROPERTY;

public class HttpServiceEncodedUriTestCase extends AbstractHttpServerTestCase {

  @Rule
  public SystemProperty encodedSlashes = new SystemProperty(HTTP_SERVICE_ENCODED_SLASH_ENABLED_PROPERTY, "true");

  private static final String SIMPLE_ENDPOINT = "test";
  private static final String PARAM_ENDPOINT = "test/{value}/test2";
  private static final String SECOND_ENDPOINT = "test/test3";
  private static final String PARAM_SIMPLE_ENDPOINT = "test/{value}";
  private static final String PAYLOAD1 = "p1";
  private static final String PAYLOAD2 = "p2";
  private static final String PAYLOAD3 = "p3";
  private static final String PAYLOAD4 = "p4";

  public HttpServiceEncodedUriTestCase(String serviceToLoad) {
    super(serviceToLoad);
  }

  @Before
  public void setup() {
    setProperty(HTTP_SERVICE_ENCODED_SLASH_ENABLED_PROPERTY, "true");
  }

  @After
  public void tearDown() {
    clearProperty(HTTP_SERVICE_ENCODED_SLASH_ENABLED_PROPERTY);
  }

  @Override
  protected String getServerName() {
    return "encoding-slash-test";
  }

  @Before
  public void setUp() throws Exception {
    setUpServer();
    registerHandler(GET, SIMPLE_ENDPOINT, PAYLOAD1);
    registerHandler(GET, SECOND_ENDPOINT, PAYLOAD2);
    registerHandler(GET, PARAM_ENDPOINT, PAYLOAD3);
    registerHandler(GET, PARAM_SIMPLE_ENDPOINT, PAYLOAD4);
  }

  private void registerHandler(HttpConstants.Method httpMethod, String endpoint, String payload) {
    server.addRequestHandler(singletonList(httpMethod.name()), "/" + endpoint, (requestContext, responseCallback) -> {
      responseCallback.responseReady(HttpResponse.builder().entity(new ByteArrayHttpEntity(payload.getBytes()))
          .addHeader(CONTENT_TYPE, TEXT.toRfcString())
          .build(), new IgnoreResponseStatusCallback());
    });
  }

  @Test
  public void simpleTest() throws Exception {
    assertPostRequestGetsOKResponseStatusAndPayload(SIMPLE_ENDPOINT, of(PAYLOAD1));
  }

  @Test
  public void withParametersWithoutEncoding() throws Exception {
    assertPostRequestGetsOKResponseStatusAndPayload(SIMPLE_ENDPOINT + "/zaraza", of(PAYLOAD4));
  }

  @Test
  public void withParametersWithEncodedSlash() throws Exception {
    assertPostRequestGetsOKResponseStatusAndPayload(SIMPLE_ENDPOINT + "/%2Fzaraza%20%2F", of(PAYLOAD4));
  }

  @Test
  public void twoNamesUri() throws Exception {
    assertPostRequestGetsOKResponseStatusAndPayload(SIMPLE_ENDPOINT + "/test3", of(PAYLOAD2));
  }

  @Test
  public void encodedSlashesDontWorkAsSeparators() throws Exception {
    assertPostRequestGetsOKResponseStatusAndPayload(SIMPLE_ENDPOINT + "%2Ftest3");
  }

  @Test
  public void encodedSlashesDontWorkAsSeparatorsEvenEncodingPercentage() throws Exception {
    assertPostRequestGetsOKResponseStatusAndPayload(SIMPLE_ENDPOINT + "%252Ftest3");
  }

  @Test
  public void innerParameterCorrectlyTaken() throws Exception {
    assertPostRequestGetsOKResponseStatusAndPayload(SIMPLE_ENDPOINT + "/%2Ftest2/test2", of(PAYLOAD3));
  }

  @Test
  public void innerParameterCorrectlyTakenWithEncodedPercentage() throws Exception {
    assertPostRequestGetsOKResponseStatusAndPayload(SIMPLE_ENDPOINT + "/%252Ftest2/test2", of(PAYLOAD3));
  }

  @Test
  public void innerParameterWithMultipleEncodedSlashesBeginning() throws Exception {
    assertPostRequestGetsOKResponseStatusAndPayload(SIMPLE_ENDPOINT + "/%2Fte%20st2%2F/test2/", of(PAYLOAD3));
  }

  @Test
  public void innerParameterWithMultipleEncodedSlashes() throws Exception {
    assertPostRequestGetsOKResponseStatusAndPayload(SIMPLE_ENDPOINT + "/zaraza%2Fte%20st2%2F/test2/", of(PAYLOAD3));
  }

  @Test
  public void encodedSlashesDontSupresefutureSlashes() throws Exception {
    assertPostRequestGetsOKResponseStatusAndPayload(SIMPLE_ENDPOINT + "/%2Fte%20st2%2F/%2F/");
  }

  protected void assertPostRequestGetsOKResponseStatusAndPayload(String endpoint) throws IOException {
    assertPostRequestGetsOKResponseStatusAndPayload(endpoint, empty());
  }

  protected void assertPostRequestGetsOKResponseStatusAndPayload(String endpoint, Optional<String> payload) throws IOException {
    Request request = Request.Get(format("http://%s:%s/%s", server.getServerAddress().getIp(), port.getValue(), endpoint));

    org.apache.http.HttpResponse response = request.execute().returnResponse();
    StatusLine statusLine = response.getStatusLine();

    if (payload.isPresent()) {
      assertThat(statusLine.getStatusCode(), is(OK.getStatusCode()));
      assertThat(IOUtils.toString(response.getEntity().getContent()), is(payload.get()));
    } else {
      assertThat(statusLine.getStatusCode(), is(NOT_FOUND.getStatusCode()));
    }
  }
}
