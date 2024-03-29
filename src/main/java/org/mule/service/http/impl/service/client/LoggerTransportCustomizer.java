/*
 * Copyright 2023 Salesforce, Inc. All rights reserved.
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.service.http.impl.service.client;

import static java.lang.Thread.currentThread;
import static org.mule.service.http.impl.service.HttpMessageLogger.LoggerType.REQUESTER;

import org.mule.runtime.api.exception.MuleRuntimeException;
import org.mule.service.http.impl.service.HttpMessageLogger;

import com.ning.http.client.providers.grizzly.TransportCustomizer;

import org.glassfish.grizzly.filterchain.Filter;
import org.glassfish.grizzly.filterchain.FilterChainBuilder;
import org.glassfish.grizzly.http.HttpCodecFilter;
import org.glassfish.grizzly.nio.transport.TCPNIOTransport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Transport customizer that adds a probe for logging HTTP messages.
 */
public class LoggerTransportCustomizer implements TransportCustomizer {

  private static final Logger logger = LoggerFactory.getLogger(LoggerTransportCustomizer.class);
  private final String identifier;

  public LoggerTransportCustomizer(String identifier) {
    this.identifier = identifier;
  }

  @Override
  public void customize(TCPNIOTransport transport, FilterChainBuilder filterChainBuilder) {
    HttpCodecFilter httpCodecFilter = findHttpCodecFilter(filterChainBuilder);
    httpCodecFilter.getMonitoringConfig()
        .addProbes(new HttpMessageLogger(REQUESTER, identifier, currentThread().getContextClassLoader()));
  }

  private HttpCodecFilter findHttpCodecFilter(FilterChainBuilder filterChainBuilder) {
    HttpCodecFilter httpCodecFilter = null;
    try {
      int i = 0;
      do {
        Filter filter = filterChainBuilder.get(i);
        if (filter instanceof HttpCodecFilter) {
          httpCodecFilter = (HttpCodecFilter) filter;
        }
        i++;
      } while (httpCodecFilter == null);
    } catch (IndexOutOfBoundsException e) {
      logger.error(String.format("Failure looking for %s in grizzly client transport", HttpCodecFilter.class.getName()));
      throw new MuleRuntimeException(e);
    }
    return httpCodecFilter;
  }
}
