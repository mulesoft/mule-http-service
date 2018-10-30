/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.service.http.impl.service.server;

import static java.lang.String.format;
import static java.net.URLEncoder.encode;
import static java.util.Collections.singletonList;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.core.IsInstanceOf.instanceOf;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.junit.Assert.assertThat;
import static org.hamcrest.core.StringEndsWith.endsWith;
import static org.hamcrest.core.StringContains.containsString;
import static org.apache.commons.text.StringEscapeUtils.escapeHtml4;
import static org.mule.runtime.api.metadata.MediaType.TEXT;
import static org.mule.runtime.http.api.HttpConstants.Method.GET;
import static org.mule.runtime.http.api.HttpConstants.Method.POST;
import static org.mule.runtime.http.api.HttpConstants.HttpStatus.BAD_REQUEST;
import static org.mule.runtime.http.api.HttpConstants.HttpStatus.OK;
import static org.mule.runtime.http.api.HttpHeaders.Names.CONTENT_TYPE;

import org.apache.http.StatusLine;
import org.apache.http.client.ClientProtocolException;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.HashMap;
import java.util.Map;
import org.apache.http.client.fluent.Request;
import org.mule.runtime.core.api.util.IOUtils;
import org.mule.runtime.http.api.HttpConstants;
import org.mule.runtime.http.api.domain.entity.ByteArrayHttpEntity;
import org.mule.runtime.http.api.server.PathAndMethodRequestMatcher;
import org.mule.runtime.http.api.server.MethodRequestMatcher;
import org.mule.runtime.http.api.domain.message.request.HttpRequest;
import org.mule.runtime.http.api.domain.message.response.HttpResponse;
import org.mule.runtime.http.api.server.RequestHandler;
import org.mule.service.http.impl.functional.server.AbstractHttpServerTestCase;
import org.mule.service.http.impl.service.util.SocketRequester;

public class HttpServiceMalformedUrlTestCase extends AbstractHttpServerTestCase {

  private static final String LINE_SEPARATOR = System.lineSeparator();
  private static final String MALFORMED = "/api/ping%";
  private static final String WILDCARD = "/*";

  public static final String MALFORMED_SCRIPT = "<script></script>%";
  public static final String ENCODED_SPACE = "test/foo 1 %";
  public static final String ENCODED_HASHTAG = "test/foo 1 #";
  public static final String ENCODED_PERCENT2 = "test/%24";

  public HttpServiceMalformedUrlTestCase(String serviceToLoad) {
    super(serviceToLoad);
  }

  @Override
  protected String getServerName() {
    return "malformedurl-test";
  }

  @Before
  public void setUp() throws Exception {
    setUpServer();
    registerHandler(GET, WILDCARD.substring(1), "Success!");
    registerHandler(POST, ENCODED_SPACE, "test-payload1");
    registerHandler(POST, ENCODED_HASHTAG, "test-payload2");
    registerHandler(POST, ENCODED_PERCENT2, "test-payload3");

  }

  private void registerHandler(HttpConstants.Method httpMethod, String endpoint, String payload) {
    server.addRequestHandler(singletonList(httpMethod.name()), "/" + endpoint, (requestContext, responseCallback) -> {
      responseCallback.responseReady(HttpResponse.builder().entity(new ByteArrayHttpEntity(payload.getBytes()))
          .addHeader(CONTENT_TYPE, TEXT.toRfcString())
          .build(), new IgnoreResponseStatusCallback());
    });
  }

  @Test
  public void returnsBadRequestOnMalformedUrlForWildcardEndpoint() throws Exception {
    SocketRequester socketRequester = new SocketRequester(server.getServerAddress().getIp(), port.getNumber());
    try {
      socketRequester.initialize();
      socketRequester.doRequest("GET " + MALFORMED + " HTTP/1.1");
      String response = socketRequester.getResponse();
      assertThat(response, containsString(Integer.toString(BAD_REQUEST.getStatusCode())));
      assertThat(response, containsString(BAD_REQUEST.getReasonPhrase()));
      assertThat(response, endsWith("Unable to parse request: " + MALFORMED + getRequestEnding()));
    } finally {
      socketRequester.finalizeGracefully();
    }
  }

  @Test
  public void returnsBadRequestOnMalformedUrlWithInvalidContentTypeWithScript() throws Exception {
    SocketRequester socketRequester = new SocketRequester(server.getServerAddress().getIp(), port.getNumber());;
    try {
      socketRequester.initialize();
      socketRequester.doRequest("POST " + MALFORMED_SCRIPT + " HTTP/1.1");
      String response = socketRequester.getResponse();
      MatcherAssert.assertThat(response, Matchers.containsString(Integer.toString(BAD_REQUEST.getStatusCode())));
      MatcherAssert.assertThat(response, Matchers.containsString(BAD_REQUEST.getReasonPhrase()));
      MatcherAssert.assertThat(response, Matchers.endsWith(escapeHtml4(MALFORMED_SCRIPT + getRequestEnding())));
    } finally {
      socketRequester.finalizeGracefully();
    }
  }

  @Test
  public void httpListenerRegistryReturnsBadRequestHandlerOnMalformedUrl() {
    Map<String, RequestHandler> requestHandlerPerPath = new HashMap<>();
    HttpListenerRegistry listenerRegistry = new HttpListenerRegistry();
    RequestHandler getHandler = mock(RequestHandler.class);

    requestHandlerPerPath.put(WILDCARD, getHandler);

    //Register mock GET handler for wildcard endpoint.
    listenerRegistry.addRequestHandler(server, requestHandlerPerPath.get(WILDCARD), PathAndMethodRequestMatcher.builder()
        .methodRequestMatcher(MethodRequestMatcher.builder().add(GET).build())
        .path(WILDCARD)
        .build());

    final HttpRequest mockRequest = createMockRequestWithPath(MALFORMED);
    when(mockRequest.getMethod()).thenReturn(GET.name());
    Assert.assertThat(listenerRegistry.getRequestHandler(server.getServerAddress(), mockRequest),
                      instanceOf(BadRequestHandler.class));

  }

  @Test
  public void returnsOKWithEndocodedPathForSpecificEndpointSpace() throws Exception {
    assertPostRequestGetsOKResponseStatusAndPayload(ENCODED_SPACE, "test-payload1");
  }

  @Test
  public void returnsOKWithEndocodedPathForSpecificEndpointHashtag() throws Exception {
    assertPostRequestGetsOKResponseStatusAndPayload(ENCODED_HASHTAG, "test-payload2");
  }

  @Test
  public void returnsOKWithEndocodedPathForSpecificEndpointPercent() throws Exception {
    assertPostRequestGetsOKResponseStatusAndPayload(ENCODED_PERCENT2, "test-payload3");
  }

  protected void assertPostRequestGetsOKResponseStatusAndPayload(String endpoint, String payload)
      throws UnsupportedEncodingException, ClientProtocolException, IOException {
    Request request = Request.Post(getUrl(endpoint));

    org.apache.http.HttpResponse response = request.execute().returnResponse();
    StatusLine statusLine = response.getStatusLine();

    MatcherAssert.assertThat(statusLine.getStatusCode(), is(OK.getStatusCode()));
    MatcherAssert.assertThat(IOUtils.toString(response.getEntity().getContent()), is(payload));

  }

  protected HttpRequest createMockRequestWithPath(String path) {
    final HttpRequest mockRequest = mock(HttpRequest.class);
    when(mockRequest.getPath()).thenReturn(path);

    return mockRequest;
  }

  protected String getRequestEnding() {
    return LINE_SEPARATOR + "0" + LINE_SEPARATOR;
  }

  protected String getUrl(String path) throws UnsupportedEncodingException {
    return format("http://%s:%s/%s", server.getServerAddress().getIp(), port.getValue(), encode(path, UTF_8.displayName()));
  }

}
