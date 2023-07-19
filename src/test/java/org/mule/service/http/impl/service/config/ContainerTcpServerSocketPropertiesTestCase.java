/*
 * Copyright 2023 Salesforce, Inc. All rights reserved.
 */
package org.mule.service.http.impl.service.config;

import static java.io.File.separator;
import static java.lang.System.clearProperty;
import static java.lang.System.getProperty;
import static java.lang.System.setProperty;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertThat;
import static org.mule.runtime.core.api.config.MuleProperties.MULE_HOME_DIRECTORY_PROPERTY;
import static org.mule.runtime.core.api.util.ClassUtils.getClassPathRoot;
import static org.mule.service.http.impl.AllureConstants.HttpFeature.HTTP_SERVICE;
import static org.mule.service.http.impl.config.ContainerTcpServerSocketProperties.SERVER_SOCKETS_FILE;
import org.mule.runtime.api.exception.MuleException;
import org.mule.service.http.impl.config.ContainerTcpServerSocketProperties;
import org.mule.tck.junit4.AbstractMuleTestCase;

import io.qameta.allure.Feature;
import org.junit.Test;

@Feature(HTTP_SERVICE)
public class ContainerTcpServerSocketPropertiesTestCase extends AbstractMuleTestCase {

  @Test
  public void usesDefaultProperties() throws MuleException {
    ContainerTcpServerSocketProperties properties = ContainerTcpServerSocketProperties.loadTcpServerSocketProperties();

    assertThat(properties.getSendBufferSize(), is(nullValue()));
    assertThat(properties.getReceiveBufferSize(), is(nullValue()));
    assertThat(properties.getClientTimeout(), is(nullValue()));
    assertThat(properties.getSendTcpNoDelay(), is(true));
    assertThat(properties.getLinger(), is(nullValue()));
    assertThat(properties.getKeepAlive(), is(false));
    assertThat(properties.getReuseAddress(), is(true));
    assertThat(properties.getReceiveBacklog(), is(50));
    assertThat(properties.getServerTimeout(), is(60000));
  }

  @Test
  public void loadsPropertiesFromMuleHome() throws MuleException {
    String muleHome = getProperty(MULE_HOME_DIRECTORY_PROPERTY);
    try {
      setProperty(MULE_HOME_DIRECTORY_PROPERTY,
                  getClassPathRoot(ContainerTcpServerSocketPropertiesTestCase.class).getPath());
      ContainerTcpServerSocketProperties properties = ContainerTcpServerSocketProperties.loadTcpServerSocketProperties();

      assertThat(properties.getSendBufferSize(), is(1024));
      assertThat(properties.getReceiveBufferSize(), is(2048));
      assertThat(properties.getClientTimeout(), is(60000));
      assertThat(properties.getSendTcpNoDelay(), is(false));
      assertThat(properties.getLinger(), is(30000));
      assertThat(properties.getKeepAlive(), is(true));
      assertThat(properties.getReuseAddress(), is(false));
      assertThat(properties.getReceiveBacklog(), is(42));
      assertThat(properties.getServerTimeout(), is(20000));
    } finally {
      restore(MULE_HOME_DIRECTORY_PROPERTY, muleHome);
    }
  }

  @Test
  public void loadsPropertiesFromOverrideFile() throws MuleException {
    String muleHome = getProperty(MULE_HOME_DIRECTORY_PROPERTY);
    String overrideFile = getProperty(SERVER_SOCKETS_FILE);
    try {
      String testResourcePath = getClassPathRoot(ContainerTcpServerSocketPropertiesTestCase.class).getPath();
      setProperty(MULE_HOME_DIRECTORY_PROPERTY, testResourcePath);
      setProperty(SERVER_SOCKETS_FILE, testResourcePath + separator + "http-server-sockets-override.conf");
      ContainerTcpServerSocketProperties properties = ContainerTcpServerSocketProperties.loadTcpServerSocketProperties();

      assertThat(properties.getSendBufferSize(), is(2048));
      assertThat(properties.getReceiveBufferSize(), is(1024));
      assertThat(properties.getClientTimeout(), is(30000));
      assertThat(properties.getSendTcpNoDelay(), is(true));
      assertThat(properties.getLinger(), is(60000));
      assertThat(properties.getKeepAlive(), is(false));
      assertThat(properties.getReuseAddress(), is(true));
      assertThat(properties.getReceiveBacklog(), is(96));
      assertThat(properties.getServerTimeout(), is(30000));
    } finally {
      restore(MULE_HOME_DIRECTORY_PROPERTY, muleHome);
      restore(SERVER_SOCKETS_FILE, overrideFile);
    }
  }

  private void restore(String property, String value) {
    if (value != null) {
      setProperty(property, value);
    } else {
      clearProperty(property);
    }
  }



}
