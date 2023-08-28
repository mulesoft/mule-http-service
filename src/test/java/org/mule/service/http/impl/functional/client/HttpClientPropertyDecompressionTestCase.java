/*
 * Copyright 2023 Salesforce, Inc. All rights reserved.
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.service.http.impl.functional.client;

import static org.mule.service.http.impl.service.client.GrizzlyHttpClient.refreshSystemProperties;
import org.mule.runtime.http.api.client.HttpClientConfiguration;
import org.mule.tck.junit4.rule.SystemProperty;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;

public class HttpClientPropertyDecompressionTestCase extends AbstractHttpClientDecompressionTestCase {

  @Rule
  public SystemProperty decompressionProperty = new SystemProperty("mule.http.client.decompress", "true");

  public HttpClientPropertyDecompressionTestCase(String serviceToLoad) {
    super(serviceToLoad);
  }

  @Before
  public void propertySetUp() {
    refreshSystemProperties();
  }

  @After
  public void propertyTearDown() {
    refreshSystemProperties();
  }

  @Override
  protected HttpClientConfiguration getClientConfiguration() {
    return new HttpClientConfiguration.Builder()
        .setName("decompression-property-test")
        .build();
  }
}
