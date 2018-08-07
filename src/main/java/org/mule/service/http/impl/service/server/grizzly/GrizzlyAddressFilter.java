package org.mule.service.http.impl.service.server.grizzly;

import org.glassfish.grizzly.filterchain.BaseFilter;
import org.glassfish.grizzly.filterchain.Filter;
import org.mule.runtime.http.api.server.ServerAddress;

public interface GrizzlyAddressFilter<F extends BaseFilter> extends Filter {

  /**
   * Adds a new Filter for a particular Server address
   *
   * @param serverAddress the server address to which this filter must be applied
   * @param filter        the filter to apply
   */
  void addFilterForAddress(ServerAddress serverAddress, F filter);

  /**
   * Removes a Filter for a particular Server address
   *
   * @param serverAddress the server address to which this filter must be removed
   */
  void removeFilterForAddress(ServerAddress serverAddress);

  /**
   * Check if ther eis a filter for the specified server address
   *
   * @return true if contains filter for address, false otherwise
   */
  boolean hasFilterForAddress(ServerAddress serverAddress);
}
