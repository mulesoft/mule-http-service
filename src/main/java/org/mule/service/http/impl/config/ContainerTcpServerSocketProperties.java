/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.service.http.impl.config;

import static java.io.File.separator;
import static java.lang.System.getProperty;
import static org.mule.runtime.core.api.config.MuleProperties.MULE_HOME_DIRECTORY_PROPERTY;
import static org.slf4j.LoggerFactory.getLogger;
import org.mule.runtime.api.exception.DefaultMuleException;
import org.mule.runtime.api.exception.MuleException;
import org.mule.runtime.http.api.tcp.TcpServerSocketProperties;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;
import java.util.function.Consumer;

import org.slf4j.Logger;

/**
 * Contains the TCP server socket configuration for the Mule Runtime's HTTP service.
 *
 * @since 1.2
 */
public class ContainerTcpServerSocketProperties implements TcpServerSocketProperties {

  public static final String SERVER_SOCKETS_FILE = "mule.httpServerSockets.confFile";
  public static final String PROPERTY_PREFIX = "org.mule.runtime.http.server.socket.";

  private static final Logger LOGGER = getLogger(ContainerTcpServerSocketProperties.class);

  private Integer sendBufferSize;
  private Integer receiveBufferSize;
  private Integer clientTimeout;
  private Boolean sendTcpNoDelay = true;
  private Integer linger;
  private Boolean keepAlive = false;
  private Boolean reuseAddress = true;
  private Integer receiveBacklog = 50;
  private Integer serverTimeout = 60000;

  private ContainerTcpServerSocketProperties() {

  }

  public static ContainerTcpServerSocketProperties loadTcpServerSocketProperties() throws MuleException {
    ContainerTcpServerSocketProperties socketProperties = new ContainerTcpServerSocketProperties();
    File configFile;

    String overrideFile = getProperty(SERVER_SOCKETS_FILE);

    if (overrideFile != null) {
      configFile = new File(overrideFile);
    } else {
      File muleHome =
          getProperty(MULE_HOME_DIRECTORY_PROPERTY) != null ? new File(getProperty(MULE_HOME_DIRECTORY_PROPERTY)) : null;

      if (muleHome == null) {
        LOGGER.info("No " + MULE_HOME_DIRECTORY_PROPERTY + " defined. Using default server sockets configuration.");
        return socketProperties;
      }

      configFile = new File(muleHome, "conf" + separator + "http-server-sockets.conf");
    }

    if (!configFile.exists()) {
      LOGGER.info("Server sockets config file not found. Using default values.");
      return socketProperties;
    }

    if (LOGGER.isDebugEnabled()) {
      LOGGER.debug("Loading server sockets configuration from " + configFile.getPath());
    }

    final Properties properties = new Properties();
    try (final FileInputStream configIs = new FileInputStream(configFile)) {
      properties.load(configIs);
    } catch (IOException e) {
      throw new DefaultMuleException(e);
    }

    ifPresent("sendBufferSize", properties, p -> socketProperties.setSendBufferSize(Integer.valueOf(p)));
    ifPresent("receiveBufferSize", properties, p -> socketProperties.setReceiveBufferSize(Integer.valueOf(p)));
    ifPresent("clientTimeout", properties, p -> socketProperties.setClientTimeout(Integer.valueOf(p)));
    ifPresent("sendTcpNoDelay", properties, p -> socketProperties.setSendTcpNoDelay(Boolean.valueOf(p)));
    ifPresent("linger", properties, p -> socketProperties.setLinger(Integer.valueOf(p)));
    ifPresent("keepAlive", properties, p -> socketProperties.setKeepAlive(Boolean.valueOf(p)));
    ifPresent("reuseAddress", properties, p -> socketProperties.setReuseAddress(Boolean.valueOf(p)));
    ifPresent("receiveBacklog", properties, p -> socketProperties.setReceiveBacklog(Integer.valueOf(p)));
    ifPresent("serverTimeout", properties, p -> socketProperties.setServerTimeout(Integer.valueOf(p)));

    return socketProperties;
  }

  private static void ifPresent(String propertyName, Properties properties, Consumer<String> operation) {
    String property = properties.getProperty(PROPERTY_PREFIX + propertyName);
    if (property != null) {
      operation.accept(property);
    }
  }

  @Override
  public Integer getSendBufferSize() {
    return sendBufferSize;
  }

  @Override
  public Integer getReceiveBufferSize() {
    return receiveBufferSize;
  }

  @Override
  public Integer getClientTimeout() {
    return clientTimeout;
  }

  @Override
  public Boolean getSendTcpNoDelay() {
    return sendTcpNoDelay;
  }

  @Override
  public Integer getLinger() {
    return linger;
  }

  @Override
  public Boolean getKeepAlive() {
    return keepAlive;
  }

  @Override
  public Boolean getReuseAddress() {
    return reuseAddress;
  }

  @Override
  public Integer getReceiveBacklog() {
    return receiveBacklog;
  }

  @Override
  public Integer getServerTimeout() {
    return serverTimeout;
  }

  private void setSendBufferSize(Integer sendBufferSize) {
    this.sendBufferSize = sendBufferSize;
  }

  private void setReceiveBufferSize(Integer receiveBufferSize) {
    this.receiveBufferSize = receiveBufferSize;
  }

  private void setClientTimeout(Integer clientTimeout) {
    this.clientTimeout = clientTimeout;
  }

  private void setSendTcpNoDelay(Boolean sendTcpNoDelay) {
    this.sendTcpNoDelay = sendTcpNoDelay;
  }

  private void setLinger(Integer linger) {
    this.linger = linger;
  }

  private void setKeepAlive(Boolean keepAlive) {
    this.keepAlive = keepAlive;
  }

  private void setReuseAddress(Boolean reuseAddress) {
    this.reuseAddress = reuseAddress;
  }

  private void setReceiveBacklog(Integer receiveBacklog) {
    this.receiveBacklog = receiveBacklog;
  }

  private void setServerTimeout(Integer serverTimeout) {
    this.serverTimeout = serverTimeout;
  }
}
