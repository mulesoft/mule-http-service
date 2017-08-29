/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.service.http.impl.functional;

import static junit.framework.TestCase.fail;
import static org.slf4j.LoggerFactory.getLogger;
import org.mule.runtime.core.api.util.ClassUtils;
import org.mule.runtime.http.api.server.async.ResponseStatusCallback;
import org.mule.service.http.impl.service.HttpServiceImplementation;
import org.mule.tck.SimpleUnitTestSupportSchedulerService;
import org.mule.tck.junit4.AbstractMuleTestCase;

import org.junit.After;
import org.junit.Before;
import org.slf4j.Logger;

/**
 * Base class for all HTTP service functional tests. The service implementation will be loaded based on it's class name, allowing
 * tests to customize it.
 */
public class AbstractHttpServiceTestCase extends AbstractMuleTestCase {

  private static final Logger LOGGER = getLogger(AbstractHttpServiceTestCase.class);

  public static String serviceToLoad = HttpServiceImplementation.class.getName();

  protected HttpServiceImplementation service;
  private SimpleUnitTestSupportSchedulerService schedulerService;

  @Before
  public void createServices() throws Exception {
    schedulerService = new SimpleUnitTestSupportSchedulerService();
    service = (HttpServiceImplementation) ClassUtils.instantiateClass(serviceToLoad, new Object[] {schedulerService},
                                                                      this.getClass().getClassLoader());
    LOGGER.info("Running HTTP service test using implementation: " + serviceToLoad);
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
