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

import org.mule.service.http.impl.service.HttpServiceImplementation;

import io.qameta.allure.Feature;
import io.qameta.allure.Story;

/**
 * Test for the communication between the SSE Source (client-side), and the SSE Endpoint (server-side).
 */
@Feature(HTTP_SERVICE)
@Story(SSE)
@Story(SSE_SOURCE)
@Story(SSE_ENDPOINT)
public class GrizzlySseTestCase extends org.mule.service.http.common.sse.SseTestCase {

  public GrizzlySseTestCase(String serviceToLoad) {
    super(HttpServiceImplementation.class.getName());
  }
}
