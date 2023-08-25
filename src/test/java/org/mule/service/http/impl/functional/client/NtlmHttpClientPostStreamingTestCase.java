/*
 * Copyright 2023 Salesforce, Inc. All rights reserved.
 */
package org.mule.service.http.impl.functional.client;

import static org.mule.service.http.impl.AllureConstants.HttpFeature.HttpStory.STREAMING;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

import io.qameta.allure.Story;
import io.qameta.allure.junit4.DisplayName;

@Story(STREAMING)
@DisplayName("Validates that the POST cursor body is preserved on NTLM authentication")
public class NtlmHttpClientPostStreamingTestCase extends AbstractNtlmHttpClientPostStreamingTestCase {

  public NtlmHttpClientPostStreamingTestCase(String serviceToLoad) {
    super(serviceToLoad);
  }

  @Override
  protected InputStream getInputStream() {
    return new ByteArrayInputStream(TEST_PAYLOAD.getBytes());
  }

}
