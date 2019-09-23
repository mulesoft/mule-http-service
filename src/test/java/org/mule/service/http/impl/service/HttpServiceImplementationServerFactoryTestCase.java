/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.service.http.impl.service;

import static java.util.Optional.empty;
import static java.util.Optional.of;
import static org.junit.rules.ExpectedException.none;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mule.runtime.core.api.config.MuleProperties.APP_NAME_PROPERTY;
import static org.mule.runtime.core.api.config.MuleProperties.DOMAIN_NAME_PROPERTY;
import static org.mule.runtime.core.api.config.bootstrap.ArtifactType.APP;
import static org.mule.runtime.core.api.config.bootstrap.ArtifactType.DOMAIN;
import static org.mule.runtime.core.api.config.bootstrap.ArtifactType.PLUGIN;
import static org.mule.runtime.core.api.config.bootstrap.ArtifactType.POLICY;
import org.mule.runtime.api.artifact.Registry;
import org.mule.runtime.api.exception.MuleRuntimeException;
import org.mule.runtime.core.api.MuleContext;
import org.mule.runtime.core.api.config.bootstrap.ArtifactType;
import org.mule.runtime.http.api.server.HttpServerConfiguration;
import org.mule.runtime.http.api.server.HttpServerFactory;
import org.mule.runtime.http.api.server.ServerNotFoundException;
import org.mule.tck.SimpleUnitTestSupportSchedulerService;

import java.util.Optional;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class HttpServiceImplementationServerFactoryTestCase {

  private static final HttpServerConfiguration serverConfiguration = new HttpServerConfiguration.Builder()
      .setName("CONFIG_NAME")
      .setHost("localhost")
      .setPort(8081)
      .build();

  private HttpServiceImplementation httpService;

  @Before
  public void setUp() throws Exception {
    httpService = new HttpServiceImplementation(new SimpleUnitTestSupportSchedulerService());
    httpService.start();
  }

  @Rule
  public ExpectedException expectedException = none();

  @Test
  public void failureWhenServerForUnknownArtifactType() {
    expectedException.expect(MuleRuntimeException.class);
    expectedException.expectMessage("Unable to create HttpServerFactory for artifact with type: PLUGIN, with properties");
    newServerFactory(empty(), empty(), PLUGIN);
  }

  @Test
  public void failureWhenServerForAPPWithNoName() {
    expectedException.expect(MuleRuntimeException.class);
    expectedException.expectMessage("Could not create server factory for application");
    newServerFactory(empty(), empty(), APP);
  }

  @Test
  public void failureWhenServerForDOMAINWithNoName() {
    expectedException.expect(MuleRuntimeException.class);
    expectedException.expectMessage("Could not create server factory for domain");
    newServerFactory(empty(), empty(), DOMAIN);
  }

  @Test
  public void failureWhenServerForPOLICYWithNoName() {
    expectedException.expect(MuleRuntimeException.class);
    expectedException.expectMessage("Could not create server factory for policy");
    newServerFactory(empty(), empty(), POLICY);
  }

  @Test
  public void sameAppAccessSameServer() throws Exception {
    final String app = "app";
    HttpServerFactory factory = newAppServerFactory(app, empty());

    //No failure means OK. Can't compare servers because they are different implementations
    factory.create(serverConfiguration);
    factory.lookup(serverConfiguration.getName());
  }

  @Test
  public void sameDomainAccessSameServer() throws Exception {
    final String domain = "domain";
    HttpServerFactory factory = newDomainServerFactory(domain);

    //No failure means OK. Can't compare servers because they are different implementations
    factory.create(serverConfiguration);
    factory.lookup(serverConfiguration.getName());
  }

  @Test
  public void samePolicyAccessSameServer() throws Exception {
    final String policy = "policy";
    HttpServerFactory factory = newPolicyServerFactory(policy);

    //No failure means OK. Can't compare servers because they are different implementations
    factory.create(serverConfiguration);
    factory.lookup(serverConfiguration.getName());
  }

  @Test
  public void appsAccessServerFromDomain() throws Exception {
    final String app1 = "app1";
    final String app2 = "app2";
    final String domain = "domain";

    HttpServerFactory appFactory1 = newAppServerFactory(app1, of(domain));
    HttpServerFactory appFactory2 = newAppServerFactory(app2, of(domain));
    HttpServerFactory domainFactory = newDomainServerFactory(domain);

    //No failure means OK. Can't compare servers because they are different implementations
    domainFactory.create(serverConfiguration);
    appFactory1.lookup(serverConfiguration.getName());
    appFactory2.lookup(serverConfiguration.getName());
  }

  @Test
  public void policyCantAccessAppServer() throws Exception {
    final String policy = "policy";
    final String app = "app";

    HttpServerFactory appFactory = newAppServerFactory(app, empty());
    HttpServerFactory policyFactory = newPolicyServerFactory(policy);

    appFactory.create(serverConfiguration);

    expectedException.expect(ServerNotFoundException.class);
    policyFactory.lookup(serverConfiguration.getName());
  }

  @Test
  public void policyCantAccessAppServerWithSameNames() throws Exception {
    final String name = "sameName";

    HttpServerFactory appFactory = newAppServerFactory(name, empty());
    HttpServerFactory policyFactory = newPolicyServerFactory(name);

    appFactory.create(serverConfiguration);

    expectedException.expect(ServerNotFoundException.class);
    policyFactory.lookup(serverConfiguration.getName());
  }

  @Test
  public void appCantAccessDomainServerWithSameNames() throws Exception {
    final String name = "sameName";

    HttpServerFactory appFactory = newAppServerFactory(name, empty());
    HttpServerFactory domainFactory = newDomainServerFactory(name);

    domainFactory.create(serverConfiguration);

    expectedException.expect(ServerNotFoundException.class);
    appFactory.lookup(serverConfiguration.getName());
  }

  @Test
  public void differentAppsDontAccessSameServers() throws Exception {
    final String app1 = "app1";
    final String app2 = "app2";
    HttpServerFactory factory1 = newAppServerFactory(app1, empty());
    HttpServerFactory factory2 = newAppServerFactory(app2, empty());

    factory1.create(serverConfiguration);
    expectedException.expect(ServerNotFoundException.class);
    factory2.lookup(serverConfiguration.getName());
  }


  private HttpServerFactory newAppServerFactory(String appName, Optional<String> domainName) {
    return newServerFactory(of(appName), domainName, APP);
  }

  private HttpServerFactory newDomainServerFactory(String domainName) {
    return newServerFactory(empty(), of(domainName), DOMAIN);
  }

  private HttpServerFactory newPolicyServerFactory(String policyName) {
    return newServerFactory(of(policyName), empty(), POLICY);
  }

  //Use this to test invalid configurations. For valid configurations use the specific factory methods
  private HttpServerFactory newServerFactory(Optional<String> artifactName, Optional<String> domainName,
                                             ArtifactType artifactType) {
    MuleContext muleContext = mock(MuleContext.class);
    when(muleContext.getArtifactType()).thenReturn(artifactType);

    Registry registry = mock(Registry.class);

    when(registry.<String>lookupByName(APP_NAME_PROPERTY)).thenReturn(artifactName);
    when(registry.<String>lookupByName(DOMAIN_NAME_PROPERTY)).thenReturn(domainName);

    return httpService.getServerFactory(registry, muleContext);
  }

}
