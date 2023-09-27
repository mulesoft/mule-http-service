/*
 * Copyright 2023 Salesforce, Inc. All rights reserved.
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.service.http.impl.functional;

import static org.mule.service.http.impl.AllureConstants.HttpFeature.HTTP_SERVICE;
import static org.mule.service.http.impl.functional.util.ClassUtils.instantiateClass;

import static java.util.Collections.singletonList;

import static com.github.peterwippermann.junit4.parameterizedsuite.ParameterContext.getParameter;
import static com.github.peterwippermann.junit4.parameterizedsuite.ParameterContext.isParameterSet;
import static junit.framework.TestCase.fail;

import org.mule.runtime.api.lifecycle.Stoppable;
import org.mule.runtime.api.scheduler.SchedulerService;
import org.mule.runtime.extension.api.annotation.param.Parameter;
import org.mule.runtime.http.api.server.async.ResponseStatusCallback;
import org.mule.service.http.impl.service.HttpServiceImplementation;
import org.mule.tck.SimpleUnitTestSupportSchedulerService;
import org.mule.tck.junit4.AbstractMuleTestCase;

import org.junit.After;
import org.junit.Before;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import io.qameta.allure.Feature;

/**
 * Base class for all HTTP service functional tests. The service implementation will be loaded based on it's class name, allowing
 * tests to customize it.
 */
@RunWith(Parameterized.class)
@Feature(HTTP_SERVICE)
public abstract class AbstractHttpServiceTestCase extends AbstractMuleTestCase {

  @Parameter
  public String serviceToLoad;

  protected HttpServiceImplementation service;
  private SchedulerService schedulerService;

  @Parameters(name = "{0}")
  public static Iterable<Object[]> params() {
    if (isParameterSet()) {
      return singletonList(getParameter(Object[].class));
    } else {
      return singletonList(new String[] {HttpServiceImplementation.class.getName()});
    }
  }

  public AbstractHttpServiceTestCase(String serviceToLoad) {
    this.serviceToLoad = serviceToLoad;
  }

  @Before
  public void createServices() throws Exception {
    schedulerService = getSchedulerService();
    service = (HttpServiceImplementation) instantiateClass(serviceToLoad, new Object[] {schedulerService},
                                                           this.getClass().getClassLoader());
    service.start();
  }

  protected SchedulerService getSchedulerService() {
    return new SimpleUnitTestSupportSchedulerService();
  }

  @After
  public void closeServices() throws Exception {
    if (service != null) {
      service.stop();
    }
    if (schedulerService instanceof Stoppable) {
      ((Stoppable) schedulerService).stop();
    }
  }

  public static class IgnoreResponseStatusCallback implements ResponseStatusCallback {

    @Override
    public void responseSendFailure(Throwable throwable) {
      fail(throwable.getMessage());
    }

    @Override
    public void responseSendSuccessfully() {
      // Do nothing
    }
  }

}
