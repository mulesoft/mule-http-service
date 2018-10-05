/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.service.http.impl.service.util;

import static java.util.Objects.requireNonNull;
import org.mule.runtime.http.api.utils.RequestMatcherRegistry;

import java.util.function.Supplier;

public class DefaultRequestMatcherRegistryBuilder<T> implements RequestMatcherRegistry.RequestMatcherRegistryBuilder<T> {

  private static final Supplier NULL_SUPPLIER = () -> null;

  private Supplier<T> onMethodMismatch = NULL_SUPPLIER;
  private Supplier<T> onNotFound = NULL_SUPPLIER;
  private Supplier<T> onDisabled = NULL_SUPPLIER;

  @Override
  public RequestMatcherRegistry.RequestMatcherRegistryBuilder<T> onMethodMismatch(Supplier<T> itemSupplier) {
    requireNonNull(itemSupplier, "A method mismatch item supplier must be specified.");
    onMethodMismatch = itemSupplier;
    return this;
  }

  @Override
  public RequestMatcherRegistry.RequestMatcherRegistryBuilder<T> onNotFound(Supplier<T> itemSupplier) {
    requireNonNull(itemSupplier, "A not found item supplier must be specified.");
    onNotFound = itemSupplier;
    return this;
  }

  @Override
  public RequestMatcherRegistry.RequestMatcherRegistryBuilder<T> onDisabled(Supplier<T> itemSupplier) {
    requireNonNull(itemSupplier, "A disabled item supplier must be specified.");
    onDisabled = itemSupplier;
    return this;
  }

  @Override
  public RequestMatcherRegistry<T> build() {
    return new DefaultRequestMatcherRegistry<>(onMethodMismatch, onNotFound, onDisabled);
  }
}
