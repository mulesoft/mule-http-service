/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.service.http.impl.functional.client;

import static org.mule.service.http.impl.AllureConstants.HttpFeature.HttpStory.STREAMING;

import java.io.InputStream;

import org.mule.weave.v2.el.ByteArrayBasedCursorStream;

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
    return new ByteArrayBasedCursorStream(TEST_PAYLOAD.getBytes(), null);
  }

}
