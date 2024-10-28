/*
 * Copyright 2023 Salesforce, Inc. All rights reserved.
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.service.http.impl.provider;

import static org.mule.service.http.impl.provider.HttpServiceProvider.GRIZZLY_IMPLEMENTATION_NAME;
import static org.mule.service.http.impl.provider.HttpServiceProvider.NETTY_IMPLEMENTATION_NAME;
import static org.mule.service.http.impl.provider.HttpServiceProvider.getImplementationName;
import static org.mule.tck.MuleTestUtils.testWithSystemProperty;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThrows;

import org.mule.runtime.api.service.Service;
import org.mule.service.http.impl.service.HttpServiceImplementation;
import org.mule.service.http.netty.impl.service.NettyHttpServiceImplementation;
import org.mule.tck.junit4.AbstractMuleTestCase;

import org.junit.Test;

public class HttpServiceProviderTestCase extends AbstractMuleTestCase {

  public static final String MULE_HTTP_SERVICE_IMPLEMENTATION_SYSTEM_PROPERTY = "mule.http.service.implementation";

  @Test
  public void grizzlyPropertyByDefault() throws Exception {
    testWithSystemProperty(MULE_HTTP_SERVICE_IMPLEMENTATION_SYSTEM_PROPERTY, null,
                           () -> assertThat(getImplementationName(), is(GRIZZLY_IMPLEMENTATION_NAME)));
  }

  @Test
  public void grizzlyPropertyIfConfigured() throws Exception {
    testWithSystemProperty(MULE_HTTP_SERVICE_IMPLEMENTATION_SYSTEM_PROPERTY, "GRIZZLY",
                           () -> assertThat(getImplementationName(), is(GRIZZLY_IMPLEMENTATION_NAME)));
  }

  @Test
  public void nettyPropertyIfConfigured() throws Exception {
    testWithSystemProperty(MULE_HTTP_SERVICE_IMPLEMENTATION_SYSTEM_PROPERTY, "NETTY",
                           () -> assertThat(getImplementationName(), is(NETTY_IMPLEMENTATION_NAME)));
  }

  @Test
  public void invalidPropertyThrows() throws Exception {
    testWithSystemProperty(MULE_HTTP_SERVICE_IMPLEMENTATION_SYSTEM_PROPERTY, "INVALID",
                           () -> assertThrows("Unknown HTTP Service implementation 'INVALID'. Choose 'GRIZZLY' or 'NETTY'",
                                              IllegalArgumentException.class,
                                              HttpServiceProvider::getImplementationName));
  }

  @Test
  public void grizzlyImplementationByDefault() throws Exception {
    testWithSystemProperty(MULE_HTTP_SERVICE_IMPLEMENTATION_SYSTEM_PROPERTY, null,
                           () -> assertThat(getImplementationClass(), is(HttpServiceImplementation.class)));
  }

  @Test
  public void grizzlyImplementationIfConfigured() throws Exception {
    testWithSystemProperty(MULE_HTTP_SERVICE_IMPLEMENTATION_SYSTEM_PROPERTY, "GRIZZLY",
                           () -> assertThat(getImplementationClass(), is(HttpServiceImplementation.class)));
  }

  @Test
  public void nettyImplementationIfConfigured() throws Exception {
    testWithSystemProperty(MULE_HTTP_SERVICE_IMPLEMENTATION_SYSTEM_PROPERTY, "NETTY",
                           () -> assertThat(getImplementationClass(), is(NettyHttpServiceImplementation.class)));
  }

  @Test
  public void invalidImplementationThrows() throws Exception {
    testWithSystemProperty(MULE_HTTP_SERVICE_IMPLEMENTATION_SYSTEM_PROPERTY, "INVALID",
                           () -> assertThrows("Unknown HTTP Service implementation 'INVALID'. Choose 'GRIZZLY' or 'NETTY'",
                                              IllegalArgumentException.class,
                                              this::getImplementationClass));
  }

  public Class<? extends Service> getImplementationClass() {
    return new HttpServiceProvider().getServiceDefinition().getService().getClass();
  }
}
