/*
 * Copyright 2023 Salesforce, Inc. All rights reserved.
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.service.http.impl.service.server.grizzly;

import org.mule.runtime.http.api.server.ServerAddress;
import org.mule.service.http.impl.service.server.ServerAddressMap;

import org.glassfish.grizzly.Connection;
import org.glassfish.grizzly.filterchain.BaseFilter;
import org.glassfish.grizzly.filterchain.FilterChain;
import org.glassfish.grizzly.filterchain.FilterChainContext;
import org.glassfish.grizzly.filterchain.FilterChainEvent;
import org.glassfish.grizzly.filterchain.NextAction;

import java.io.IOException;
import java.net.InetSocketAddress;

/**
 * Grizzly filter to delegate to the right {link @BaseFilter} based on the Connection.
 * <p>
 * Mule allows to define several listener config, each one associated with a ServerSocket that may have particular configurations.
 * In order to reuse the same grizzly transport we can only have one filter for every listener config. So this filter keeps record
 * of all the ServerSockets configured and their particular configurations. So once a request arrive it delegates to the right
 * filter based on the connection being processed.
 */
public class GrizzlyAddressDelegateFilter<F extends BaseFilter> extends BaseFilter implements GrizzlyAddressFilter<F> {

  private ServerAddressMap<F> filters = new ServerAddressMap<>();

  @Override
  public void onAdded(FilterChain filterChain) {
    super.onAdded(filterChain);
  }

  @Override
  public void onFilterChainChanged(FilterChain filterChain) {
    super.onFilterChainChanged(filterChain);
  }

  @Override
  public void onRemoved(FilterChain filterChain) {
    super.onRemoved(filterChain);
  }

  @Override
  public NextAction handleRead(FilterChainContext ctx) throws IOException {
    F filter = retrieveFilter(ctx.getConnection());
    if (filter != null) {
      return filter.handleRead(ctx);
    }
    return super.handleRead(ctx);
  }

  @Override
  public NextAction handleWrite(FilterChainContext ctx) throws IOException {
    F filter = retrieveFilter(ctx.getConnection());
    if (filter != null) {
      return filter.handleWrite(ctx);
    }
    return super.handleWrite(ctx);
  }

  @Override
  public NextAction handleConnect(FilterChainContext ctx) throws IOException {
    F filter = retrieveFilter(ctx.getConnection());
    if (filter != null) {
      return filter.handleConnect(ctx);
    }
    return super.handleConnect(ctx);
  }

  @Override
  public NextAction handleAccept(FilterChainContext ctx) throws IOException {
    F filter = retrieveFilter(ctx.getConnection());
    if (filter != null) {
      return filter.handleAccept(ctx);
    }
    return super.handleAccept(ctx);
  }

  @Override
  public NextAction handleEvent(FilterChainContext ctx, FilterChainEvent event) throws IOException {
    F filter = retrieveFilter(ctx.getConnection());
    if (filter != null) {
      return filter.handleEvent(ctx, event);
    }
    return super.handleEvent(ctx, event);
  }

  @Override
  public NextAction handleClose(FilterChainContext ctx) throws IOException {
    F filter = retrieveFilter(ctx.getConnection());
    if (filter != null) {
      return filter.handleClose(ctx);
    }
    return super.handleClose(ctx);
  }

  @Override
  public void exceptionOccurred(FilterChainContext ctx, Throwable error) {
    F filter = retrieveFilter(ctx.getConnection());
    if (filter != null) {
      filter.exceptionOccurred(ctx, error);
    }
    super.exceptionOccurred(ctx, error);
  }

  @Override
  public FilterChainContext createContext(Connection connection, FilterChainContext.Operation operation) {
    F filter = retrieveFilter(connection);
    if (filter != null) {
      return filter.createContext(connection, operation);
    }
    return super.createContext(connection, operation);
  }


  private F retrieveFilter(Connection connection) {
    final InetSocketAddress inetAddress = (InetSocketAddress) connection.getLocalAddress();
    return filters.get(inetAddress.getAddress(), inetAddress.getPort());
  }

  /**
   * Adds a new Filter for a particular Server address
   *
   * @param serverAddress the server address to which this filter must be applied
   * @param filter        the filter to apply
   */
  @Override
  public synchronized void addFilterForAddress(ServerAddress serverAddress, F filter) {
    filters.put(serverAddress, filter);
  }

  @Override
  public synchronized void removeFilterForAddress(ServerAddress serverAddress) {
    if (filters.containsKey(serverAddress)) {
      filters.remove(serverAddress);
    }
  }

  @Override
  public synchronized boolean hasFilterForAddress(ServerAddress serverAddress) {
    return filters.containsKey(serverAddress);
  }
}
