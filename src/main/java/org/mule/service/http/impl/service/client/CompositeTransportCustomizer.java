/*
 * Copyright 2023 Salesforce, Inc. All rights reserved.
 */
package org.mule.service.http.impl.service.client;

import com.ning.http.client.providers.grizzly.TransportCustomizer;

import java.util.ArrayList;
import java.util.List;

import org.glassfish.grizzly.filterchain.FilterChainBuilder;
import org.glassfish.grizzly.nio.transport.TCPNIOTransport;

public class CompositeTransportCustomizer implements TransportCustomizer {

  private List<TransportCustomizer> transportCustomizers = new ArrayList<>();

  @Override
  public void customize(TCPNIOTransport transport, FilterChainBuilder filterChainBuilder) {
    for (TransportCustomizer transportCustomizer : transportCustomizers) {
      transportCustomizer.customize(transport, filterChainBuilder);
    }
  }

  public void addTransportCustomizer(TransportCustomizer transportCustomizer) {
    transportCustomizers.add(transportCustomizer);
  }
}
