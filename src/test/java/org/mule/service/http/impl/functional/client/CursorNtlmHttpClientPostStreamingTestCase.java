/*
 * Copyright 2023 Salesforce, Inc. All rights reserved.
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.service.http.impl.functional.client;

import static org.mule.service.http.impl.AllureConstants.HttpFeature.HttpStory.STREAMING;

import org.mule.weave.v2.el.ByteArrayBasedCursorStreamProvider;

import java.io.InputStream;

import org.junit.jupiter.api.DisplayName;

import io.qameta.allure.Story;

@Story(STREAMING)
@DisplayName("Validates that the POST cursor body is preserved on NTLM authentication")
public class CursorNtlmHttpClientPostStreamingTestCase extends AbstractNtlmHttpClientPostStreamingTestCase {

  public CursorNtlmHttpClientPostStreamingTestCase(String serviceToLoad) {
    super(serviceToLoad);
  }

  @Override
  protected InputStream getInputStream() {
    return new ByteArrayBasedCursorStreamProvider(TEST_PAYLOAD.getBytes()).doOpenCursor();
  }

}
