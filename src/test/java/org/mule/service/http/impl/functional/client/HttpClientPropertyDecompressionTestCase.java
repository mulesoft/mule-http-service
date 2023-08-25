/*
 * Copyright 2023 Salesforce, Inc. All rights reserved.
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
