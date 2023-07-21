/*
 * Copyright 2023 Salesforce, Inc. All rights reserved.
 */
package org.mule.service.http.impl.service.util;

import static java.util.Objects.requireNonNull;
import static org.mule.service.http.impl.service.util.DefaultRequestMatcherRegistry.NULL_SUPPLIER;
import org.mule.runtime.http.api.utils.RequestMatcherRegistry;

import java.util.function.Supplier;

public class DefaultRequestMatcherRegistryBuilder<T> implements RequestMatcherRegistry.RequestMatcherRegistryBuilder<T> {

  private Supplier<T> onMethodMismatch = NULL_SUPPLIER;
  private Supplier<T> onNotFound = NULL_SUPPLIER;
  private Supplier<T> onDisabled = NULL_SUPPLIER;
  private Supplier<T> onInvalidRequest = NULL_SUPPLIER;

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
  public RequestMatcherRegistry.RequestMatcherRegistryBuilder<T> onInvalidRequest(Supplier<T> itemSupplier) {
    requireNonNull(itemSupplier, "An invalid item supplier must be specified.");
    onInvalidRequest = itemSupplier;
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
    return new DefaultRequestMatcherRegistry<>(onMethodMismatch, onNotFound, onInvalidRequest, onDisabled);
  }
}
