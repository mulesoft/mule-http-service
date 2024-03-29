/*
 * Copyright 2023 Salesforce, Inc. All rights reserved.
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
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
