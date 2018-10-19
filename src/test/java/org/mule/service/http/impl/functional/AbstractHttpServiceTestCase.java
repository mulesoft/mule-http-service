/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.service.http.impl.functional;

import static junit.framework.TestCase.fail;
import static org.mule.service.http.impl.AllureConstants.HttpFeature.HTTP_SERVICE;
import org.mule.runtime.core.api.util.ClassUtils;
import org.mule.runtime.extension.api.annotation.param.Parameter;
import org.mule.runtime.http.api.server.async.ResponseStatusCallback;
import org.mule.service.http.impl.service.HttpServiceImplementation;
import org.mule.tck.SimpleUnitTestSupportSchedulerService;
import org.mule.tck.junit4.AbstractMuleTestCase;

import com.github.peterwippermann.junit4.parameterizedsuite.ParameterContext;

import java.util.Collections;

import io.qameta.allure.Feature;
import org.junit.After;
import org.junit.Before;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

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
  private SimpleUnitTestSupportSchedulerService schedulerService;

  @Parameters(name = "{0}")
  public static Iterable<Object[]> params() {
    if (ParameterContext.isParameterSet()) {
      return Collections.singletonList(ParameterContext.getParameter(Object[].class));
    } else {
      return Collections.singletonList(new String[] {HttpServiceImplementation.class.getName()});
    }
  }

  public AbstractHttpServiceTestCase(String serviceToLoad) {
    this.serviceToLoad = serviceToLoad;
  }

  @Before
  public void createServices() throws Exception {
    schedulerService = new SimpleUnitTestSupportSchedulerService();
    service = (HttpServiceImplementation) ClassUtils.instantiateClass(serviceToLoad, new Object[] {schedulerService},
                                                                      this.getClass().getClassLoader());
    service.start();
  }

  @After
  public void closeServices() throws Exception {
    if (service != null) {
      service.stop();
    }
    if (schedulerService != null) {
      schedulerService.stop();
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
