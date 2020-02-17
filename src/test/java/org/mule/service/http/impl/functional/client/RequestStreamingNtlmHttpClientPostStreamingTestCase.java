/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.service.http.impl.functional.client;

import static org.mule.service.http.impl.AllureConstants.HttpFeature.HttpStory.STREAMING;

import org.junit.After;
import org.junit.Before;

import io.qameta.allure.Story;
import io.qameta.allure.junit4.DisplayName;

@Story(STREAMING)
@DisplayName("Validates that the POST cursor body is preserved on NTLM authentication with streaming on request")
public class RequestStreamingNtlmHttpClientPostStreamingTestCase extends NtlmHttpClientPostStreamingTestCase {


  @Before
  public void before() throws Exception {
    setRequestStreaming(true);
  }

  public RequestStreamingNtlmHttpClientPostStreamingTestCase(String serviceToLoad) {
    super(serviceToLoad);
  }

  @After
  public void after() throws Exception {
    setRequestStreaming(false);
  }

}
