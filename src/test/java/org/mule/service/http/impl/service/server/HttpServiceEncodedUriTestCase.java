/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.service.http.impl.service.server;

import org.apache.http.StatusLine;
import org.apache.http.client.fluent.Request;
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

import static java.lang.String.format;
import static java.util.Collections.singletonList;
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
  public void test1() throws Exception {
    assertPostRequestGetsOKResponseStatusAndPayload(SIMPLE_ENDPOINT, PAYLOAD1);
  }

  @Test
  public void test2() throws Exception {
    assertPostRequestGetsOKResponseStatusAndPayload(SIMPLE_ENDPOINT + "/zaraza", PAYLOAD4);
  }

  @Test
  public void test3() throws Exception {
    assertPostRequestGetsOKResponseStatusAndPayload(SIMPLE_ENDPOINT + "/test3", PAYLOAD2);
  }

  @Test
  public void test4() throws Exception {
    assertPostRequestGetsOKResponseStatusAndPayload(SIMPLE_ENDPOINT + "%2Ftest3", PAYLOAD2, false);
  }

  @Test
  public void test5() throws Exception {
    assertPostRequestGetsOKResponseStatusAndPayload(SIMPLE_ENDPOINT + "/%2Ftest2/test2", PAYLOAD3);
  }

  @Test
  public void test6() throws Exception {
    assertPostRequestGetsOKResponseStatusAndPayload(SIMPLE_ENDPOINT + "/%2Fte%20st2%2F/test2/", PAYLOAD3);
  }

  protected void assertPostRequestGetsOKResponseStatusAndPayload(String endpoint, String payload) throws IOException {
    assertPostRequestGetsOKResponseStatusAndPayload(endpoint, payload, true);
  }

  protected void assertPostRequestGetsOKResponseStatusAndPayload(String endpoint, String payload, boolean okExpected)
      throws IOException {
    Request request = Request.Get(format("http://%s:%s/%s", server.getServerAddress().getIp(), port.getValue(), endpoint));

    org.apache.http.HttpResponse response = request.execute().returnResponse();
    StatusLine statusLine = response.getStatusLine();

    if (okExpected) {
      assertThat(statusLine.getStatusCode(), is(OK.getStatusCode()));
      assertThat(IOUtils.toString(response.getEntity().getContent()), is(payload));
    } else {
      assertThat(statusLine.getStatusCode(), is(NOT_FOUND.getStatusCode()));
    }
  }
}
