/*
 * Copyright 2023 Salesforce, Inc. All rights reserved.
 */
package org.mule.service.http.impl.functional.client;

import static org.mule.service.http.impl.AllureConstants.HttpFeature.HttpStory.STREAMING;

import org.mule.weave.v2.el.ByteArrayBasedCursorStreamProvider;

import java.io.InputStream;

import io.qameta.allure.Story;
import io.qameta.allure.junit4.DisplayName;

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
