/*
 * Copyright 2023 Salesforce, Inc. All rights reserved.
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.service.http.impl.util;

import org.mule.runtime.api.exception.MuleException;
import org.mule.runtime.api.lifecycle.Stoppable;
import org.mule.runtime.api.scheduler.Scheduler;
import org.mule.runtime.api.scheduler.SchedulerConfig;
import org.mule.runtime.api.scheduler.SchedulerPoolsConfigFactory;
import org.mule.runtime.api.scheduler.SchedulerService;
import org.mule.runtime.api.scheduler.SchedulerView;

import java.util.List;

public class SchedulerServiceAdapter implements SchedulerService, Stoppable {

  protected SchedulerService schedulerServiceDelegate;

  public SchedulerServiceAdapter(SchedulerService schedulerServiceDelegate) {
    this.schedulerServiceDelegate = schedulerServiceDelegate;
  }

  @Override
  public String getName() {
    return schedulerServiceDelegate.getName();
  }

  @Override
  public Scheduler cpuLightScheduler() {
    return schedulerServiceDelegate.cpuLightScheduler();
  }

  @Override
  public Scheduler ioScheduler() {
    return schedulerServiceDelegate.ioScheduler();
  }

  @Override
  public Scheduler cpuIntensiveScheduler() {
    return schedulerServiceDelegate.cpuIntensiveScheduler();
  }

  @Override
  public Scheduler cpuLightScheduler(SchedulerConfig config) {
    return schedulerServiceDelegate.cpuLightScheduler(config);
  }

  @Override
  public Scheduler ioScheduler(SchedulerConfig config) {
    return schedulerServiceDelegate.ioScheduler(config);
  }

  @Override
  public Scheduler cpuIntensiveScheduler(SchedulerConfig config) {
    return schedulerServiceDelegate.cpuIntensiveScheduler(config);
  }

  @Override
  public Scheduler cpuLightScheduler(SchedulerConfig config, SchedulerPoolsConfigFactory poolsConfigFactory) {
    return schedulerServiceDelegate.cpuLightScheduler(config, poolsConfigFactory);
  }

  @Override
  public Scheduler ioScheduler(SchedulerConfig config, SchedulerPoolsConfigFactory poolsConfigFactory) {
    return schedulerServiceDelegate.ioScheduler(config, poolsConfigFactory);
  }

  @Override
  public Scheduler cpuIntensiveScheduler(SchedulerConfig config, SchedulerPoolsConfigFactory poolsConfigFactory) {
    return schedulerServiceDelegate.cpuIntensiveScheduler(config, poolsConfigFactory);
  }

  @Override
  public Scheduler customScheduler(SchedulerConfig config) {
    return schedulerServiceDelegate.customScheduler(config);
  }

  @Override
  public Scheduler customScheduler(SchedulerConfig config, int queueSize) {
    return schedulerServiceDelegate.customScheduler(config, queueSize);
  }

  @Override
  public List<SchedulerView> getSchedulers() {
    return schedulerServiceDelegate.getSchedulers();
  }

  @Override
  public void stop() throws MuleException {
    if (schedulerServiceDelegate instanceof Stoppable) {
      ((Stoppable) schedulerServiceDelegate).stop();
    }
  }
}
