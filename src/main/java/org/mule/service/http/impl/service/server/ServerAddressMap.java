/*
 * Copyright 2023 Salesforce, Inc. All rights reserved.
 */
package org.mule.service.http.impl.service.server;

import static org.mule.runtime.http.api.HttpConstants.ALL_INTERFACES_ADDRESS;

import org.mule.runtime.http.api.server.ServerAddress;

import java.net.InetAddress;
import java.util.HashMap;
import java.util.Map;

/**
 * This is a wrapper for a map whose keys are {@link ServerAddress}s. It makes sure that if an entry is not found we instead
 * search for an entry with that same port but host 0.0.0.0.
 */
public class ServerAddressMap<T> {

  private Map<ServerAddress, T> internalMap;
  private boolean specificAddressPresent;

  public ServerAddressMap() {
    this(new HashMap<>());
  }

  public ServerAddressMap(Map<ServerAddress, T> internalMap) {
    this.internalMap = internalMap;
    checkForSpecificAddresses();
  }

  private void checkForSpecificAddresses() {
    specificAddressPresent = this.internalMap.keySet().stream().anyMatch(sa -> !ALL_INTERFACES_ADDRESS.equals(sa.getAddress()));
  }

  public void put(ServerAddress serverAddress, T value) {
    internalMap.put(serverAddress, value);
    checkForSpecificAddresses();
  }

  public T get(Object key) {
    if (specificAddressPresent) {
      T value = internalMap.get(key);
      if (value == null) {
        // if there's no entry for the specific address, we need to check if there's one for all interfaces address.
        value = internalMap.get(new DefaultServerAddress(ALL_INTERFACES_ADDRESS, ((ServerAddress) key).getPort()));
      }
      return value;
    } else {
      return internalMap.get(new DefaultServerAddress(ALL_INTERFACES_ADDRESS, ((ServerAddress) key).getPort()));
    }
  }

  public T get(InetAddress address, int port) {
    return get(new DefaultServerAddress(address, port));
  }

  public boolean containsKey(Object key) {
    return internalMap.containsKey(key);
  }

  public T remove(Object key) {
    T removed = internalMap.remove(key);
    checkForSpecificAddresses();
    return removed;
  }
}
