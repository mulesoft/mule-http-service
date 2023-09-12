/*
 * Copyright 2023 Salesforce, Inc. All rights reserved.
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
import org.mule.api.annotation.jpms.ServiceModule;

/**
 * Grizzly based implementation of the HTTP Service.
 *
 * @moduleGraph
 * @since 1.5
 */
@ServiceModule
module org.mule.service.http {

  requires org.mule.runtime.http.api;
  requires org.mule.runtime.core;

  requires grizzly.framework;
  requires grizzly.http;
  requires grizzly.http.client;
  requires grizzly.websockets;

  requires jakarta.mail;
  requires java.inject;
  requires java.logging;

  requires com.github.benmanes.caffeine;
  requires com.google.common;
  requires org.apache.commons.io;
  requires org.apache.commons.text;
  requires org.slf4j;

  // Allow invocation and injection into providers by the Mule Runtime
  exports org.mule.service.http.impl.provider to
      org.mule.runtime.service;
  exports org.mule.service.http.impl.service to
      org.mule.runtime.service,
      com.mulesoft.mule.service.http.ee;
  opens org.mule.service.http.impl.provider to
      org.mule.runtime.service;

  exports org.mule.service.http.impl.config to
      com.mulesoft.mule.service.http.ee;
  exports org.mule.service.http.impl.service.client to
      com.mulesoft.mule.service.http.ee;
  exports org.mule.service.http.impl.service.server to
      com.mulesoft.mule.service.http.ee;
  exports org.mule.service.http.impl.service.server.grizzly to
      com.mulesoft.mule.service.http.ee;
  exports org.mule.service.http.impl.service.util to
      com.mulesoft.mule.service.http.ee;
  exports org.mule.service.http.impl.util to
      com.mulesoft.mule.service.http.ee;
}
