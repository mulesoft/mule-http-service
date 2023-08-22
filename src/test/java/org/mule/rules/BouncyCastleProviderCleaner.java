/*
 * Copyright 2023 Salesforce, Inc. All rights reserved.
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.rules;

import static java.security.Security.removeProvider;

import java.util.HashSet;
import java.util.Set;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.jsse.provider.BouncyCastleJsseProvider;
import org.junit.rules.ExternalResource;

public class BouncyCastleProviderCleaner extends ExternalResource {

  private boolean initialized;
  private Set<String> providers;

  public BouncyCastleProviderCleaner() {
    Set<String> providers = new HashSet<>();
    providers.add(BouncyCastleProvider.PROVIDER_NAME);
    providers.add(BouncyCastleJsseProvider.PROVIDER_NAME);
    this.providers = providers;
  }

  @Override
  protected void before() throws Throwable {
    if (initialized) {
      throw new IllegalArgumentException("System property was already initialized");
    }

    cleanUpProviders();

    initialized = true;
  }

  protected void cleanUpProviders() {
    for (String p : providers) {
      removeProvider(p);
    }
  }

  @Override
  protected void after() {
    if (!initialized) {
      throw new IllegalArgumentException("Bouncy castle provider was not initialized");
    }

    cleanUpProviders();

    initialized = false;
  }

}
