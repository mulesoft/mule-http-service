/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.service.http.impl.service;

import static org.glassfish.grizzly.CloseReason.LOCALLY_CLOSED_REASON;
import static org.glassfish.grizzly.CloseReason.REMOTELY_CLOSED_REASON;
import static org.mule.runtime.api.i18n.I18nMessageFactory.createStaticMessage;
import static org.mule.runtime.api.scheduler.SchedulerConfig.config;
import static org.mule.runtime.core.api.config.MuleProperties.APP_NAME_PROPERTY;
import static org.mule.runtime.core.api.config.MuleProperties.DOMAIN_NAME_PROPERTY;
import static org.mule.runtime.core.api.config.MuleProperties.OBJECT_SCHEDULER_BASE_CONFIG;
import static org.mule.runtime.core.api.config.bootstrap.ArtifactType.APP;
import static org.mule.runtime.core.api.config.bootstrap.ArtifactType.DOMAIN;
import static org.mule.runtime.core.api.config.bootstrap.ArtifactType.POLICY;
import static org.mule.runtime.core.api.lifecycle.LifecycleUtils.disposeIfNeeded;
import static org.mule.runtime.core.api.lifecycle.LifecycleUtils.initialiseIfNeeded;
import static org.slf4j.LoggerFactory.getLogger;
import org.mule.runtime.api.artifact.Registry;
import org.mule.runtime.api.exception.MuleException;
import org.mule.runtime.api.exception.MuleRuntimeException;
import org.mule.runtime.api.lifecycle.Startable;
import org.mule.runtime.api.lifecycle.Stoppable;
import org.mule.runtime.api.scheduler.SchedulerConfig;
import org.mule.runtime.api.scheduler.SchedulerService;
import org.mule.runtime.core.api.MuleContext;
import org.mule.runtime.core.api.config.bootstrap.ArtifactType;
import org.mule.runtime.http.api.HttpService;
import org.mule.runtime.http.api.client.HttpClient;
import org.mule.runtime.http.api.client.HttpClientFactory;
import org.mule.runtime.http.api.server.HttpServer;
import org.mule.runtime.http.api.server.HttpServerFactory;
import org.mule.runtime.http.api.utils.RequestMatcherRegistry;
import org.mule.service.http.impl.service.client.HttpClientConnectionManager;
import org.mule.service.http.impl.service.server.ContextHttpServerFactoryAdapter;
import org.mule.service.http.impl.service.server.HttpListenerConnectionManager;
import org.mule.service.http.impl.service.util.DefaultRequestMatcherRegistryBuilder;

import java.util.Optional;

import javax.inject.Inject;
import javax.inject.Named;

import org.glassfish.grizzly.CloseReason;
import org.slf4j.Logger;

/**
 * Implementation of {@link HttpService} that uses Grizzly to create {@link HttpServer}s and its Async HTTP Client provider to
 * create {@link HttpClient}s.
 */
public class HttpServiceImplementation implements HttpService, Startable, Stoppable {

  static {
    // Force the initialization of CloseReason's static fields so it is done in the service classloader instead of lazily, which
    // may cause a leak of the plugin/app classloaders from the IOException generated.
    final CloseReason locallyClosedReason = LOCALLY_CLOSED_REASON;
    final CloseReason remotelyClosedReason = REMOTELY_CLOSED_REASON;
  }

  private static final Logger logger = getLogger(HttpServiceImplementation.class);
  private static final String CONTAINER_CONTEXT = "container";

  protected final SchedulerService schedulerService;

  private final HttpListenerConnectionManager listenerConnectionManager;
  private final HttpClientConnectionManager clientConnectionManager;

  public HttpServiceImplementation(SchedulerService schedulerService) {
    this.schedulerService = schedulerService;
    listenerConnectionManager = createListenerConnectionManager(schedulerService);
    clientConnectionManager = createClientConnectionManager();
  }

  @Override
  public HttpServerFactory getServerFactory() {
    return new ContextHttpServerFactoryAdapter(CONTAINER_CONTEXT, listenerConnectionManager);
  }

  @Inject
  public HttpServerFactory getServerFactory(Registry registry, MuleContext muleContext) {
    ArtifactType artifactType = muleContext.getArtifactType();
    Optional<String> appName = registry.lookupByName(APP_NAME_PROPERTY);
    Optional<String> domainName = registry.lookupByName(DOMAIN_NAME_PROPERTY);

    switch (artifactType) {

      case POLICY:
        //If this is a policy, then it's name will be set in the appName field.
        //Policies should not have the same name as applications, just in case, we will add a prefix to it's name.
        return appName
            .map(
                 name -> new ContextHttpServerFactoryAdapter(buildArtifactServerName(name, POLICY), listenerConnectionManager))
            .orElseThrow(
                         () -> new MuleRuntimeException(createStaticMessage("Could not create server factory for policy, "
                             + APP_NAME_PROPERTY + " not set")));

      case DOMAIN:
        //In case of a domain, use the populated domainName to create the context.
        return domainName
            .map(d -> new ContextHttpServerFactoryAdapter(buildArtifactServerName(d, DOMAIN), listenerConnectionManager))
            .orElseThrow(() -> new MuleRuntimeException(createStaticMessage("Could not create server factory for domain, "
                + DOMAIN_NAME_PROPERTY + " not set")));

      case APP:
        //In case of an app, we should consider the case where it belongs to a domain and use it's name as parent context
        if (domainName.isPresent() && appName.isPresent()) {
          return new ContextHttpServerFactoryAdapter(buildArtifactServerName(appName.get(), APP),
                                                     buildArtifactServerName(domainName.get(), DOMAIN),
                                                     listenerConnectionManager);
        }
        return appName
            .map(a -> new ContextHttpServerFactoryAdapter(buildArtifactServerName(a, APP), listenerConnectionManager))
            .orElseThrow(() -> new MuleRuntimeException(createStaticMessage("Could not create server factory for application, "
                + APP_NAME_PROPERTY + " not set")));

      default:
        break;
    }

    //We should never get to this point. In case we do, fail.
    throw new MuleRuntimeException(createStaticMessage("Unable to create HttpServerFactory for artifact with type: %s, with properties: %s : %s, %s : %s",
                                                       artifactType, APP_NAME_PROPERTY, appName, DOMAIN_NAME_PROPERTY,
                                                       domainName));
  }

  private String buildArtifactServerName(String name, ArtifactType artifactType) {
    return name + "-" + artifactType.getAsString();
  }

  @Override
  public HttpClientFactory getClientFactory() {
    return config -> clientConnectionManager.create(config, config());
  }

  @Inject
  public HttpClientFactory getClientFactory(@Named(OBJECT_SCHEDULER_BASE_CONFIG) SchedulerConfig schedulersConfig) {
    return config -> clientConnectionManager.create(config, schedulersConfig);
  }

  protected HttpClientConnectionManager createClientConnectionManager() {
    return new HttpClientConnectionManager(schedulerService);
  }

  protected HttpListenerConnectionManager createListenerConnectionManager(SchedulerService schedulerService) {
    return new HttpListenerConnectionManager(schedulerService, config());
  }

  @Override
  public RequestMatcherRegistry.RequestMatcherRegistryBuilder getRequestMatcherRegistryBuilder() {
    return new DefaultRequestMatcherRegistryBuilder();
  }

  @Override
  public String getName() {
    return "HTTP Service";
  }

  @Override
  public void start() throws MuleException {
    initialiseIfNeeded(listenerConnectionManager);
  }

  @Override
  public void stop() throws MuleException {
    disposeIfNeeded(listenerConnectionManager, logger);
  }
}
