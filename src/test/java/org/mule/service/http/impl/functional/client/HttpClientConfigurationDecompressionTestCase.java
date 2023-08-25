/*
 * Copyright 2023 Salesforce, Inc. All rights reserved.
 */
package org.mule.service.http.impl.functional.client;

import org.mule.runtime.http.api.client.HttpClientConfiguration;

public class HttpClientConfigurationDecompressionTestCase extends AbstractHttpClientDecompressionTestCase {

  public HttpClientConfigurationDecompressionTestCase(String serviceToLoad) {
    super(serviceToLoad);
  }

  @Override
  protected HttpClientConfiguration getClientConfiguration() {
    return new HttpClientConfiguration.Builder()
        .setDecompress(true)
        .setName("decompression-explicit-test")
        .build();
  }

}
