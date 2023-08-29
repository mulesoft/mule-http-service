/*
 * Copyright 2023 Salesforce, Inc. All rights reserved.
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.service.http.impl.service.server;

import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mule.runtime.http.api.HttpConstants.Method.GET;
import static org.mule.runtime.http.api.HttpConstants.Method.POST;
import static org.mule.runtime.http.api.HttpConstants.Method.PUT;
import static org.mule.runtime.http.api.server.MethodRequestMatcher.acceptAll;
import static org.mule.service.http.impl.AllureConstants.HttpFeature.HTTP_SERVICE;

import org.mule.runtime.api.exception.MuleRuntimeException;
import org.mule.runtime.core.api.util.StringUtils;
import org.mule.runtime.http.api.HttpConstants.Method;
import org.mule.runtime.http.api.domain.message.request.HttpRequest;
import org.mule.runtime.http.api.server.HttpServer;
import org.mule.runtime.http.api.server.MethodRequestMatcher;
import org.mule.runtime.http.api.server.PathAndMethodRequestMatcher;
import org.mule.runtime.http.api.server.RequestHandler;
import org.mule.runtime.http.api.server.RequestHandlerManager;
import org.mule.runtime.http.api.server.ServerAddress;
import org.mule.tck.junit4.AbstractMuleTestCase;
import org.mule.tck.size.SmallTest;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Map;

import io.qameta.allure.Description;
import io.qameta.allure.Feature;

@SmallTest
@Feature(HTTP_SERVICE)
public class HttpListenerRegistryTestCase extends AbstractMuleTestCase {

  public static final String TEST_IP = "127.0.0.1";
  public static final String URI_PARAM = "{uri-param}";
  public static final int TEST_PORT = 10000;
  public static final String ANOTHER_PATH = "/another-path";
  public static final String SOME_PATH = "some-path";
  public static final String SOME_OTHER_PATH = "some-other-path";
  private static final String MALFORMED = "/api/ping%";

  public static final String PATH_SEPARATOR = "/";
  public static final String ROOT_PATH = PATH_SEPARATOR;
  public static final String FIRST_LEVEL_PATH_LOWER_CASE = "/first-level-path";
  public static final String FIRST_LEVEL_PATH_UPPER_CASE = "/FIRST_LEVEL_PATH";
  public static final String FIRST_LEVEL_PATH_UPPER_CASE_CATCH_ALL = "/FIRST_LEVEL_PATH/*";
  public static final String SECOND_LEVEL_PATH = "/first-level-path/second-level";
  public static final String FIRST_LEVEL_URI_PARAM = PATH_SEPARATOR + URI_PARAM;
  public static final String FIRST_LEVEL_CATCH_ALL = "/*";
  public static final String SECOND_LEVEL_URI_PARAM = "/first-level-path/" + URI_PARAM;
  public static final String SECOND_LEVEL_CATCH_ALL = "/first-level-path/*";
  public static final String FOURTH_LEVEL_CATCH_ALL = "/another-first-level-path/second-level-path/third-level-path/*";
  public static final String URI_PARAM_IN_THE_MIDDLE = "/first-level-path/" + URI_PARAM + "/third-level-path";
  public static final String CATCH_ALL_IN_THE_MIDDLE = "/first-level-path/*/third-level-path";
  public static final String CATCH_ALL_IN_THE_MIDDLE_NO_COLLISION = "/another-first-level-path/*/third-level-path";
  public static final String SEVERAL_URI_PARAMS = "/{uri-param1}/second-level-path/{uri-param2}/fourth-level-path";
  public static final String SEVERAL_CATCH_ALL = "/*/second-level-path/*/fourth-level-path";
  public static final String METHOD_PATH_WILDCARD = "/method-path/*/";
  public static final String METHOD_PATH_URI_PARAM = "/another-method-path/{uri-param}/some-path";
  public static final String METHOD_PATH_CATCH_ALL = "/another-method-path/some-path/*";
  public static final String WILDCARD_CHARACTER = "*";

  public final RequestHandler methodPathWildcardGetRequestHandler = mock(RequestHandler.class);
  public final RequestHandler methodPathWildcardPostRequestHandler = mock(RequestHandler.class);
  public final RequestHandler methodPathUriParamGetRequestHandler = mock(RequestHandler.class);
  public final RequestHandler methodPathUriParamPostRequestHandler = mock(RequestHandler.class);
  public final RequestHandler methodPathCatchAllGetRequestHandler = mock(RequestHandler.class);
  public final RequestHandler methodPathCatchAllPostRequestHandler = mock(RequestHandler.class);

  private static InetAddress TEST_ADDRESS;
  private static ServerAddress testServerAddress;

  @Rule
  public ExpectedException expectedException = ExpectedException.none();
  private RequestHandler mockRequestHandler = mock(RequestHandler.class);
  private Map<String, RequestHandler> requestHandlerPerPath = new HashMap<>();
  private HttpListenerRegistry httpListenerRegistry;
  private HttpServer testServer;

  @BeforeClass
  public static void resolveAddresses() throws UnknownHostException {
    TEST_ADDRESS = InetAddress.getByName(TEST_IP);
    testServerAddress = new DefaultServerAddress(TEST_ADDRESS, TEST_PORT);
  }

  @Before
  public void createMockTestServer() {
    this.testServer = mock(HttpServer.class);
    when(testServer.getServerAddress()).thenReturn(testServerAddress);
  }


  @Test
  public void validateSimplePathAndAllMethodAllowedCollision() {
    final HttpListenerRegistry httpListenerRegister = new HttpListenerRegistry();
    httpListenerRegister.addRequestHandler(testServer, mock(RequestHandler.class), PathAndMethodRequestMatcher.builder()
        .methodRequestMatcher(acceptAll())
        .path(ANOTHER_PATH)
        .build());
    expectedException.expect(MuleRuntimeException.class);
    httpListenerRegister.addRequestHandler(testServer, mock(RequestHandler.class), PathAndMethodRequestMatcher.builder()
        .methodRequestMatcher(acceptAll())
        .path(ANOTHER_PATH)
        .build());
  }

  @Test
  public void validateUriParamPathAndAllMethodAllowedCollision() {
    final HttpListenerRegistry httpListenerRegister = new HttpListenerRegistry();
    httpListenerRegister
        .addRequestHandler(testServer, mock(RequestHandler.class), PathAndMethodRequestMatcher.builder()
            .methodRequestMatcher(acceptAll())
            .path(SECOND_LEVEL_URI_PARAM)
            .build());
    expectedException.expect(MuleRuntimeException.class);
    httpListenerRegister
        .addRequestHandler(testServer, mock(RequestHandler.class), PathAndMethodRequestMatcher.builder()
            .methodRequestMatcher(acceptAll())
            .path(SECOND_LEVEL_URI_PARAM)
            .build());
  }

  @Test
  public void validateCatchAllPathAndAllMethodAllowedCollision() {
    final HttpListenerRegistry httpListenerRegister = new HttpListenerRegistry();
    httpListenerRegister
        .addRequestHandler(testServer, mock(RequestHandler.class), PathAndMethodRequestMatcher.builder()
            .methodRequestMatcher(acceptAll())
            .path(SECOND_LEVEL_CATCH_ALL)
            .build());
    expectedException.expect(MuleRuntimeException.class);
    httpListenerRegister
        .addRequestHandler(testServer, mock(RequestHandler.class), PathAndMethodRequestMatcher.builder()
            .methodRequestMatcher(acceptAll())
            .path(SECOND_LEVEL_CATCH_ALL)
            .build());
  }

  @Test
  public void validateCatchAllPathAndMethodAllowedCollision() {
    final HttpListenerRegistry httpListenerRegister = new HttpListenerRegistry();
    httpListenerRegister
        .addRequestHandler(testServer, mock(RequestHandler.class), PathAndMethodRequestMatcher.builder()
            .methodRequestMatcher(MethodRequestMatcher.builder().add(GET).build())
            .path(SECOND_LEVEL_CATCH_ALL)
            .build());
    expectedException.expect(MuleRuntimeException.class);
    httpListenerRegister
        .addRequestHandler(testServer, mock(RequestHandler.class), PathAndMethodRequestMatcher.builder()
            .methodRequestMatcher(MethodRequestMatcher.builder().add(GET).build())
            .path(SECOND_LEVEL_CATCH_ALL)
            .build());
  }

  @Test
  public void validateCatchAllPathAndMethodIntersectionAllowedCollision() {
    final HttpListenerRegistry httpListenerRegister = new HttpListenerRegistry();
    httpListenerRegister
        .addRequestHandler(testServer, mock(RequestHandler.class), PathAndMethodRequestMatcher.builder()
            .methodRequestMatcher(MethodRequestMatcher.builder().add(GET).add(POST).build())
            .path(SECOND_LEVEL_CATCH_ALL)
            .build());
    expectedException.expect(MuleRuntimeException.class);
    httpListenerRegister
        .addRequestHandler(testServer, mock(RequestHandler.class), PathAndMethodRequestMatcher.builder()
            .methodRequestMatcher(MethodRequestMatcher.builder().add(PUT).add(POST).build())
            .path(SECOND_LEVEL_CATCH_ALL)
            .build());
  }

  @Test
  public void validateRootPathCollision() {
    validateCollision(ROOT_PATH, ROOT_PATH);
  }

  @Test
  public void validateFirstLevelPathCollision() {
    validateCollision(FIRST_LEVEL_PATH_LOWER_CASE, FIRST_LEVEL_PATH_LOWER_CASE);
  }

  @Test
  public void validateSecondLevelPathCollision() {
    validateCollision(SECOND_LEVEL_PATH, SECOND_LEVEL_PATH);
  }

  @Test
  public void validateNoCollisionWithSpecificAndCatchAll() {
    validateNoCollision(FIRST_LEVEL_CATCH_ALL, FIRST_LEVEL_PATH_LOWER_CASE);
  }

  @Test
  public void validateNoCollisionWithSpecificAndUriParameter() {
    validateNoCollision(FIRST_LEVEL_URI_PARAM, FIRST_LEVEL_PATH_LOWER_CASE);
  }

  @Test
  public void validateCollisionWithRootLevelCatchAllAndRootLevelCatchAll() {
    validateCollision(FIRST_LEVEL_CATCH_ALL, FIRST_LEVEL_CATCH_ALL);
  }

  @Test
  public void validateCollisionWithRootLevelUriParamAndRootLevelUriParam() {
    validateCollision(FIRST_LEVEL_URI_PARAM, FIRST_LEVEL_URI_PARAM);
  }

  @Test
  public void validateCollisionWithSecondLevelCatchAllAndSecondLevelCatchAll() {
    validateCollision(SECOND_LEVEL_CATCH_ALL, SECOND_LEVEL_CATCH_ALL);
  }

  @Test
  public void validateCollisionWithSecondLevelUriParamAndSecondLevelUriParam() {
    validateCollision(SECOND_LEVEL_URI_PARAM, SECOND_LEVEL_URI_PARAM);
  }

  @Test
  public void validateUriParamAndCatchAllInTheMiddle() {
    validateCollision(URI_PARAM_IN_THE_MIDDLE, CATCH_ALL_IN_THE_MIDDLE);
  }

  @Test
  public void validateUriParamAndUriParamInTheMiddle() {
    validateCollision(URI_PARAM_IN_THE_MIDDLE, URI_PARAM_IN_THE_MIDDLE);
  }

  @Test
  public void validateCatchAllAndUriParamInTheMiddle() {
    validateCollision(CATCH_ALL_IN_THE_MIDDLE, URI_PARAM_IN_THE_MIDDLE);
  }

  @Test
  public void validateCatchAllAndCatchAllInTheMiddle() {
    validateCollision(CATCH_ALL_IN_THE_MIDDLE, CATCH_ALL_IN_THE_MIDDLE);
  }

  @Test
  public void validateSeveralUriParamsAndSeveralUriParams() {
    validateCollision(SEVERAL_URI_PARAMS, SEVERAL_URI_PARAMS);
  }

  @Test
  public void validateSeveralUriParamsAndSeveralCatchAll() {
    validateCollision(SEVERAL_URI_PARAMS, SEVERAL_CATCH_ALL);
  }

  @Test
  public void validateSeveralCatchAllAndSeveralUriParams() {
    validateCollision(SEVERAL_CATCH_ALL, SEVERAL_URI_PARAMS);
  }

  @Test
  public void validateSeveralCatchAllAndSeveralCatchAll() {
    validateCollision(SEVERAL_CATCH_ALL, SEVERAL_CATCH_ALL);
  }

  @Test
  public void noCollisionWithCaseSensitivePaths() {
    validateNoCollision(FIRST_LEVEL_PATH_LOWER_CASE, FIRST_LEVEL_PATH_UPPER_CASE);
  }

  @Test
  public void routeToCorrectHandler() {
    httpListenerRegistry = createHttpListenerRegistryWithRegisteredHandlers();
    routePath(ROOT_PATH, ROOT_PATH);
    routePath("/something", FIRST_LEVEL_CATCH_ALL);
    routePath("/something/else", FIRST_LEVEL_CATCH_ALL);
    routePath(SECOND_LEVEL_PATH + "/somethingElse", FIRST_LEVEL_CATCH_ALL);
    routePath(SECOND_LEVEL_PATH + "somethingElse", SECOND_LEVEL_URI_PARAM);
    routePath(FIRST_LEVEL_PATH_LOWER_CASE, FIRST_LEVEL_PATH_LOWER_CASE);
    routePath(FIRST_LEVEL_PATH_LOWER_CASE + PATH_SEPARATOR, FIRST_LEVEL_PATH_LOWER_CASE);
    routePath(FIRST_LEVEL_PATH_UPPER_CASE, FIRST_LEVEL_PATH_UPPER_CASE);
    routePath(FIRST_LEVEL_PATH_UPPER_CASE + PATH_SEPARATOR, FIRST_LEVEL_PATH_UPPER_CASE);
    routePath(FIRST_LEVEL_PATH_UPPER_CASE + "/somethingElse", FIRST_LEVEL_PATH_UPPER_CASE_CATCH_ALL);
    routePath(SECOND_LEVEL_PATH, SECOND_LEVEL_PATH);
    routePath(SECOND_LEVEL_URI_PARAM.replace(URI_PARAM, "1"), SECOND_LEVEL_URI_PARAM);
    routePath(FOURTH_LEVEL_CATCH_ALL.replace(WILDCARD_CHARACTER, StringUtils.EMPTY), FOURTH_LEVEL_CATCH_ALL);
    routePath(FOURTH_LEVEL_CATCH_ALL.replace(WILDCARD_CHARACTER, "foo1/foo2"), FOURTH_LEVEL_CATCH_ALL);
    routePath(URI_PARAM_IN_THE_MIDDLE.replace(URI_PARAM, "1"), URI_PARAM_IN_THE_MIDDLE);
    routePath(URI_PARAM_IN_THE_MIDDLE.replace(URI_PARAM, "1") + ANOTHER_PATH, FIRST_LEVEL_CATCH_ALL);
    routePath(CATCH_ALL_IN_THE_MIDDLE_NO_COLLISION.replace(WILDCARD_CHARACTER, SOME_PATH), CATCH_ALL_IN_THE_MIDDLE_NO_COLLISION);
    routePath(CATCH_ALL_IN_THE_MIDDLE_NO_COLLISION.replace(WILDCARD_CHARACTER, SOME_PATH) + ANOTHER_PATH, FIRST_LEVEL_CATCH_ALL);
    routePath(SEVERAL_CATCH_ALL.replace(WILDCARD_CHARACTER, SOME_PATH), SEVERAL_CATCH_ALL);
    routePath(SEVERAL_CATCH_ALL.replace(WILDCARD_CHARACTER, SOME_PATH) + ANOTHER_PATH, FIRST_LEVEL_CATCH_ALL);
    routePath(SEVERAL_CATCH_ALL.replace(WILDCARD_CHARACTER, SOME_PATH) + ANOTHER_PATH, FIRST_LEVEL_CATCH_ALL);
    routePath(METHOD_PATH_CATCH_ALL.replace(WILDCARD_CHARACTER, ANOTHER_PATH), GET, methodPathCatchAllGetRequestHandler);
    routePath(METHOD_PATH_CATCH_ALL.replace(WILDCARD_CHARACTER, ANOTHER_PATH), POST, methodPathCatchAllPostRequestHandler);
    routePath(METHOD_PATH_URI_PARAM.replace(URI_PARAM, SOME_OTHER_PATH), GET, methodPathUriParamGetRequestHandler);
    routePath(METHOD_PATH_URI_PARAM.replace(URI_PARAM, SOME_OTHER_PATH), POST, methodPathUriParamPostRequestHandler);
    routePath(METHOD_PATH_WILDCARD.replace(WILDCARD_CHARACTER, SOME_PATH), GET, methodPathWildcardGetRequestHandler);
    routePath(METHOD_PATH_WILDCARD.replace(WILDCARD_CHARACTER, SOME_PATH), POST, methodPathWildcardPostRequestHandler);
  }

  @Test
  public void noPathFound() {
    httpListenerRegistry = new HttpListenerRegistry();
    httpListenerRegistry.addRequestHandler(testServer, mock(RequestHandler.class), PathAndMethodRequestMatcher.builder()
        .path(ROOT_PATH)
        .build());
    RequestHandler requestHandler =
        httpListenerRegistry.getRequestHandler(new DefaultServerAddress(TEST_ADDRESS, TEST_PORT),
                                               createMockRequestWithPath(ANOTHER_PATH));
    assertThat(requestHandler, is(instanceOf(NoListenerRequestHandler.class)));
  }

  @Test
  public void httpListenerRegistryReturnsBadRequestHandlerOnMalformedUrl() {
    Map<String, RequestHandler> requestHandlerPerPath = new HashMap<>();
    HttpListenerRegistry listenerRegistry = new HttpListenerRegistry();
    RequestHandler getHandler = mock(RequestHandler.class);

    requestHandlerPerPath.put(FIRST_LEVEL_CATCH_ALL, getHandler);

    // Register mock GET handler for wildcard endpoint.
    listenerRegistry.addRequestHandler(testServer, requestHandlerPerPath.get(FIRST_LEVEL_CATCH_ALL),
                                       PathAndMethodRequestMatcher.builder()
                                           .methodRequestMatcher(MethodRequestMatcher.builder().add(GET).build())
                                           .path(FIRST_LEVEL_CATCH_ALL)
                                           .build());

    final HttpRequest mockRequest = createMockRequestWithPath(MALFORMED);
    when(mockRequest.getMethod()).thenReturn(GET.name());
    assertThat(listenerRegistry.getRequestHandler(testServer.getServerAddress(), mockRequest),
               instanceOf(BadRequestHandler.class));

  }

  @Test
  public void replacePathCatchAllForCatchAll() {
    replace("/a/b/*", "/a/*", "/a/b/c", "/a/c");
  }

  @Test
  public void replacePathCatchAllForUriParam() {
    replace("/a/b/*", "/a/{b}", "/a/b/c", "/a/c");
  }

  @Test
  public void replacePathForCatchAll() {
    replace("/a/b/c", "/a/*", "/a/b/c", "/a/c");
  }

  @Test
  public void replacePathForUriParam() {
    replace("/a/b/c", "/a/{b}", "/a/b/c", "/a/c");
  }

  @Test
  public void replacePathUriParamForCatchAll() {
    replace("/a/{b}/c", "/a/*", "/a/b/c", "/a/c");
  }

  @Test
  public void replacePathUriParamForUriParam() {
    replace("/a/{b}/c", "/a/{b}", "/a/b/c", "/a/c");
  }

  @Test
  public void removingCatchAllDoesNotAffectParentPath() {
    removeChildAndCheckParent("/a", "/*", "/a/b/c");
  }

  @Test
  public void removingSubPathDoesNotAffectParentPath() {
    removeChildAndCheckParent("/a", "/b", "/a/b");
  }

  @Test
  public void removingUriParamDoesNotAffectParentPath() {
    removeChildAndCheckParent("/a", "/{b}", "/a/c");
  }

  @Test
  public void removingSubPathDoesNotAffectParentCatchAll() {
    removeChildAndCheckParent("/*", "/b", "/a/b");
  }

  @Test
  public void removingUriParamDoesNotAffectParentCatchAll() {
    removeChildAndCheckParent("/*", "/{param}", "/a/c");
  }

  @Test
  public void removingCatchAllDoesNotAffectParentUriParam() {
    removeChildAndCheckParent("/{param}", "/*", "/a/b/c");
  }

  @Test
  public void removingSubPathDoesNotAffectParentUriParam() {
    removeChildAndCheckParent("/{param}", "/b", "/a/b");
  }

  @Test
  public void removingUriParamDoesNotAffectParentUriParam() {
    removeChildAndCheckParent("/{param}", "/{param}", "/a/c");
  }

  @Test
  public void removingPathParentPathDoesNotAffectCatchAll() {
    removeParentAndCheckChild("/a", "/*", "/a/b/c");
  }

  @Test
  public void removingPathParentPathDoesNotAffectSubPath() {
    removeParentAndCheckChild("/a", "/b", "/a/b");
  }

  @Test
  public void removingParentCatchAllDoesNotAffectSubPath() {
    removeParentAndCheckChild("/*", "/b", "/a/b");
  }


  @Test
  public void removingParentUriParamDoesNotAffectSubPath() {
    removeParentAndCheckChild("/{param}", "/b", "/a/b");
  }

  @Test
  public void removingPathParentPathDoesNotAffectUriParam() {
    removeParentAndCheckChild("/a", "/{param}", "/a/c");
  }

  @Test
  public void removingParentCatchAllDoesNotAffectUriParam() {
    removeParentAndCheckChild("/*", "/{param}", "/a/c");
  }


  @Test
  public void removingParentUriParamDoesNotAffectUriParam() {
    removeParentAndCheckChild("/{param}", "/{param}", "/a/c");
  }

  @Test
  public void removingCatchAllHandlerDoesNotAffectOther() {
    removePostAndCheckGet("/*");
  }

  @Test
  public void removingPathHandlerDoesNotAffectOther() {
    removePostAndCheckGet("/a");
  }

  @Test
  public void removingUriParamHandlerDoesNotAffectOther() {
    removePostAndCheckGet("/{a}");
  }

  private void replace(String oldPath, String newPath, String oldRequestPath, String newRequestPath) {
    httpListenerRegistry = new HttpListenerRegistry();
    RequestHandler oldPathHandler = mock(RequestHandler.class);
    RequestHandlerManager oldManager =
        httpListenerRegistry.addRequestHandler(testServer, oldPathHandler, PathAndMethodRequestMatcher.builder()
            .path(oldPath)
            .build());

    routePath(oldRequestPath, GET, oldPathHandler);
    routePath(newRequestPath, GET, NoListenerRequestHandler.getInstance());

    oldManager.dispose();

    routePath(oldRequestPath, GET, NoListenerRequestHandler.getInstance());
    routePath(newRequestPath, GET, NoListenerRequestHandler.getInstance());

    RequestHandler newPathHandler = mock(RequestHandler.class);
    httpListenerRegistry.addRequestHandler(testServer, newPathHandler, PathAndMethodRequestMatcher.builder()
        .path(newPath)
        .build());

    routePath(oldRequestPath, GET,
              newPath.endsWith(WILDCARD_CHARACTER) ? newPathHandler : NoListenerRequestHandler.getInstance());
    routePath(newRequestPath, GET, newPathHandler);
  }

  private void removeChildAndCheckParent(String parent, String child, String childRequestPath) {
    httpListenerRegistry = new HttpListenerRegistry();
    RequestHandler parentHandler = mock(RequestHandler.class);
    RequestHandler childHandler = mock(RequestHandler.class);
    httpListenerRegistry.addRequestHandler(testServer, parentHandler, PathAndMethodRequestMatcher.builder()
        .path(parent)
        .build());
    RequestHandlerManager childManager = httpListenerRegistry
        .addRequestHandler(testServer, childHandler, PathAndMethodRequestMatcher.builder()
            .path(parent + child)
            .build());

    routePath("/a", GET, parentHandler);
    routePath(childRequestPath, GET, childHandler);

    childManager.dispose();

    routePath("/a", GET, parentHandler);
    routePath(childRequestPath, GET,
              parent.endsWith(WILDCARD_CHARACTER) ? parentHandler : NoListenerRequestHandler.getInstance());

    RequestHandler newChildHandler = mock(RequestHandler.class);
    httpListenerRegistry
        .addRequestHandler(testServer, newChildHandler, PathAndMethodRequestMatcher.builder()
            .path(parent + child)
            .build());

    routePath("/a", GET, parentHandler);
    routePath(childRequestPath, GET, newChildHandler);
  }

  private void removeParentAndCheckChild(String path, String child, String childRequestPath) {
    httpListenerRegistry = new HttpListenerRegistry();
    RequestHandler parentHandler = mock(RequestHandler.class);
    RequestHandler childHandler = mock(RequestHandler.class);
    RequestHandlerManager parentManager =
        httpListenerRegistry.addRequestHandler(testServer, parentHandler, PathAndMethodRequestMatcher.builder()
            .path(path)
            .build());
    httpListenerRegistry.addRequestHandler(testServer, childHandler, PathAndMethodRequestMatcher.builder()
        .path(path + child)
        .build());

    routePath("/a", GET, parentHandler);
    routePath(childRequestPath, GET, childHandler);

    parentManager.dispose();

    routePath("/a", GET, child.endsWith(WILDCARD_CHARACTER) ? childHandler : NoListenerRequestHandler.getInstance());
    routePath(childRequestPath, GET, childHandler);

    RequestHandler newParentHandler = mock(RequestHandler.class);
    httpListenerRegistry.addRequestHandler(testServer, newParentHandler, PathAndMethodRequestMatcher.builder()
        .path(path)
        .build());

    routePath("/a", GET, newParentHandler);
    routePath(childRequestPath, GET, childHandler);
  }

  private void removePostAndCheckGet(String path) {
    httpListenerRegistry = new HttpListenerRegistry();
    RequestHandler getHandler = mock(RequestHandler.class);
    RequestHandler postHandler = mock(RequestHandler.class);
    httpListenerRegistry.addRequestHandler(testServer, getHandler, PathAndMethodRequestMatcher.builder()
        .methodRequestMatcher(MethodRequestMatcher.builder().add(GET).build())
        .path(path)
        .build());
    RequestHandlerManager manager = httpListenerRegistry
        .addRequestHandler(testServer, postHandler, PathAndMethodRequestMatcher.builder()
            .methodRequestMatcher(MethodRequestMatcher.builder().add(POST).build())
            .path(path)
            .build());

    routePath("/a", GET, getHandler);
    routePath("/a", POST, postHandler);

    manager.dispose();

    routePath("/a", GET, getHandler);
    routePath("/a", POST, NoMethodRequestHandler.getInstance());

    RequestHandler newPostHandler = mock(RequestHandler.class);
    httpListenerRegistry.addRequestHandler(testServer, newPostHandler, PathAndMethodRequestMatcher.builder()
        .methodRequestMatcher(MethodRequestMatcher.builder().add(POST).build())
        .path(path)
        .build());

    routePath("/a", GET, getHandler);
    routePath("/a", POST, newPostHandler);
  }

  private void routePath(String requestPath, String listenerPath) {
    assertThat(httpListenerRegistry.getRequestHandler(new DefaultServerAddress(TEST_ADDRESS, TEST_PORT),
                                                      createMockRequestWithPath(requestPath)),
               is(requestHandlerPerPath.get(listenerPath)));
  }

  private void routePath(String requestPath, Method requestMethod, RequestHandler expectedRequestHandler) {
    final HttpRequest mockRequest = createMockRequestWithPath(requestPath);
    when(mockRequest.getMethod()).thenReturn(requestMethod.name());
    assertThat(httpListenerRegistry.getRequestHandler(new DefaultServerAddress(TEST_ADDRESS, TEST_PORT), mockRequest),
               is(expectedRequestHandler));
  }

  private HttpRequest createMockRequestWithPath(String path) {
    final HttpRequest mockRequest = mock(HttpRequest.class);
    when(mockRequest.getPath()).thenReturn(path);
    return mockRequest;
  }

  private HttpListenerRegistry createHttpListenerRegistryWithRegisteredHandlers() {
    final HttpListenerRegistry httpListenerRegistry = new HttpListenerRegistry();
    requestHandlerPerPath.put(ROOT_PATH, mock(RequestHandler.class));
    requestHandlerPerPath.put(FIRST_LEVEL_CATCH_ALL, mock(RequestHandler.class));
    requestHandlerPerPath.put(FIRST_LEVEL_PATH_LOWER_CASE, mock(RequestHandler.class));
    requestHandlerPerPath.put(FIRST_LEVEL_PATH_UPPER_CASE, mock(RequestHandler.class));
    requestHandlerPerPath.put(FIRST_LEVEL_PATH_UPPER_CASE_CATCH_ALL, mock(RequestHandler.class));
    requestHandlerPerPath.put(SECOND_LEVEL_PATH, mock(RequestHandler.class));
    requestHandlerPerPath.put(SECOND_LEVEL_URI_PARAM, mock(RequestHandler.class));
    requestHandlerPerPath.put(FOURTH_LEVEL_CATCH_ALL, mock(RequestHandler.class));
    requestHandlerPerPath.put(URI_PARAM_IN_THE_MIDDLE, mock(RequestHandler.class));
    requestHandlerPerPath.put(CATCH_ALL_IN_THE_MIDDLE_NO_COLLISION, mock(RequestHandler.class));
    requestHandlerPerPath.put(SEVERAL_CATCH_ALL, mock(RequestHandler.class));
    for (String path : requestHandlerPerPath.keySet()) {
      httpListenerRegistry.addRequestHandler(testServer, requestHandlerPerPath.get(path),
                                             PathAndMethodRequestMatcher.builder().path(path).build());
    }
    httpListenerRegistry.addRequestHandler(testServer, methodPathUriParamGetRequestHandler, PathAndMethodRequestMatcher.builder()
        .methodRequestMatcher(MethodRequestMatcher.builder().add(GET).build())
        .path(METHOD_PATH_URI_PARAM)
        .build());
    httpListenerRegistry.addRequestHandler(testServer, methodPathUriParamPostRequestHandler, PathAndMethodRequestMatcher.builder()
        .methodRequestMatcher(MethodRequestMatcher.builder().add(POST).build())
        .path(METHOD_PATH_URI_PARAM)
        .build());
    httpListenerRegistry.addRequestHandler(testServer, methodPathCatchAllGetRequestHandler, PathAndMethodRequestMatcher.builder()
        .methodRequestMatcher(MethodRequestMatcher.builder().add(GET).build())
        .path(METHOD_PATH_CATCH_ALL)
        .build());
    httpListenerRegistry.addRequestHandler(testServer, methodPathCatchAllPostRequestHandler, PathAndMethodRequestMatcher.builder()
        .methodRequestMatcher(MethodRequestMatcher.builder().add(POST).build())
        .path(METHOD_PATH_CATCH_ALL)
        .build());
    httpListenerRegistry.addRequestHandler(testServer, methodPathWildcardGetRequestHandler, PathAndMethodRequestMatcher.builder()
        .methodRequestMatcher(MethodRequestMatcher.builder().add(GET).build())
        .path(METHOD_PATH_WILDCARD)
        .build());
    httpListenerRegistry.addRequestHandler(testServer, methodPathWildcardPostRequestHandler, PathAndMethodRequestMatcher.builder()
        .methodRequestMatcher(MethodRequestMatcher.builder().add(POST).build())
        .path(METHOD_PATH_WILDCARD)
        .build());
    return httpListenerRegistry;
  }

  private void validateNoCollision(String... paths) {
    final HttpListenerRegistry httpListenerRegistry = new HttpListenerRegistry();
    for (String path : paths) {
      httpListenerRegistry.addRequestHandler(testServer, mockRequestHandler, PathAndMethodRequestMatcher.builder()
          .path(path)
          .build());
    }
  }

  private void validateCollision(String firstPath, String secondPath) {
    final HttpListenerRegistry httpListenerRegistry = new HttpListenerRegistry();
    httpListenerRegistry.addRequestHandler(testServer, mockRequestHandler, PathAndMethodRequestMatcher.builder()
        .path(firstPath)
        .build());

    expectedException.expect(MuleRuntimeException.class);
    httpListenerRegistry.addRequestHandler(testServer, mockRequestHandler, PathAndMethodRequestMatcher.builder()
        .path(secondPath)
        .build());
  }

  @Test
  @Description("Verify that a path using wildcards and a path involving a longer subpath are correctly resolved.")
  public void validateWildcardPathWithLongPath() {
    httpListenerRegistry = new HttpListenerRegistry();

    // /first-level-path/*
    requestHandlerPerPath.put(SECOND_LEVEL_CATCH_ALL, mock(RequestHandler.class));
    httpListenerRegistry.addRequestHandler(testServer, requestHandlerPerPath.get(SECOND_LEVEL_CATCH_ALL),
                                           PathAndMethodRequestMatcher.builder().path(SECOND_LEVEL_CATCH_ALL).build());

    // /first-level-path/another-path
    requestHandlerPerPath.put(SECOND_LEVEL_PATH + ANOTHER_PATH, mock(RequestHandler.class));
    httpListenerRegistry.addRequestHandler(testServer, requestHandlerPerPath.get(SECOND_LEVEL_PATH + ANOTHER_PATH),
                                           PathAndMethodRequestMatcher.builder()
                                               .path(SECOND_LEVEL_PATH + ANOTHER_PATH)
                                               .build());

    // /first-level-path/second-level/some-path --> /first-level-path/*
    routePath(SECOND_LEVEL_PATH + PATH_SEPARATOR + SOME_PATH, SECOND_LEVEL_CATCH_ALL);

    // /first-level-path/second-level/some-path/some-other-path --> /first-level-path/*
    routePath(SECOND_LEVEL_PATH + PATH_SEPARATOR + SOME_PATH + PATH_SEPARATOR + SOME_OTHER_PATH, SECOND_LEVEL_CATCH_ALL);

    // /first-level-path/another-path --> /first-level-path/another-path
    routePath(SECOND_LEVEL_PATH + ANOTHER_PATH, SECOND_LEVEL_PATH + ANOTHER_PATH);
  }


}
