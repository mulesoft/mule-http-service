/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.service.http.impl.service.server.grizzly.ws;

import static org.mule.runtime.api.util.Preconditions.checkArgument;
import static org.mule.runtime.http.api.HttpConstants.Method.GET;
import static org.mule.service.http.impl.service.server.grizzly.HttpParser.normalizePathWithSpacesOrEncodedSpaces;
import static org.mule.service.http.impl.util.HttpUtils.SLASH;
import static org.mule.service.http.impl.util.HttpUtils.WILDCARD_CHARACTER;
import org.mule.runtime.http.api.server.MethodRequestMatcher;
import org.mule.runtime.http.api.server.PathAndMethodRequestMatcher;
import org.mule.runtime.http.api.server.ws.WebSocketHandler;
import org.mule.runtime.http.api.server.ws.WebSocketHandlerManager;
import org.mule.runtime.http.api.utils.RequestMatcherRegistry;
import org.mule.service.http.impl.service.util.DefaultRequestMatcherRegistry;

public class WebSocketHandlerRegistry {

  private final RequestMatcherRegistry<WebSocketHandlerManager> registry = new DefaultRequestMatcherRegistry<>();

  public WebSocketHandlerManager addHandler(WebSocketHandler handler) {
    String path = normalizePathWithSpacesOrEncodedSpaces(handler.getPath());
    checkArgument(path.startsWith(SLASH) || path.equals(WILDCARD_CHARACTER), "path parameter must start with /");

    final GrizzlyWebSocketHandlerManager handlerManager = new GrizzlyWebSocketHandlerManager(handler);
    registry.add(pathMatcher(path), handlerManager);

    return handlerManager;
  }

  private PathAndMethodRequestMatcher pathMatcher(String path) {
    return PathAndMethodRequestMatcher.builder()
        .path(path)
        .methodRequestMatcher(MethodRequestMatcher.builder().add(GET).build())
        .build();
  }
}
