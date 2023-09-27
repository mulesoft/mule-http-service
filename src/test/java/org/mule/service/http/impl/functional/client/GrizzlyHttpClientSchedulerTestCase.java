/*
 * Copyright 2023 Salesforce, Inc. All rights reserved.
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.service.http.impl.functional.client;

import static org.mule.runtime.api.scheduler.SchedulerConfig.config;

import static java.util.concurrent.TimeUnit.SECONDS;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

import org.mule.runtime.api.scheduler.Scheduler;
import org.mule.runtime.api.scheduler.SchedulerConfig;
import org.mule.runtime.api.scheduler.SchedulerService;
import org.mule.runtime.api.util.LazyValue;
import org.mule.runtime.api.util.concurrent.Latch;
import org.mule.runtime.http.api.client.HttpClient;
import org.mule.runtime.http.api.client.HttpClientConfiguration;
import org.mule.service.http.impl.functional.AbstractHttpServiceTestCase;
import org.mule.service.http.impl.util.SchedulerAdapter;
import org.mule.service.http.impl.util.SchedulerServiceAdapter;
import org.mule.tck.SimpleUnitTestSupportSchedulerService;
import org.mule.tck.probe.JUnitLambdaProbe;
import org.mule.tck.probe.PollingProber;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import io.qameta.allure.Description;
import io.qameta.allure.Issue;
import org.junit.Before;
import org.junit.Test;

@Issue("MULE-19774")
public class GrizzlyHttpClientSchedulerTestCase extends AbstractHttpServiceTestCase {

  private final SchedulerService basicSchedulerService = new SimpleUnitTestSupportSchedulerService();
  private final LazyValue<CountingExecutesScheduler> selectorsScheduler = new LazyValue<>(this::createSelectorsScheduler);
  private final LazyValue<SchedulerService> decoratedSchedulerService = new LazyValue<>(this::createDecoratedSchedulerService);

  private static final PollingProber PROBER = new PollingProber();

  private Latch lockLatch;
  private Latch previousTaskScheduled;
  private HttpClient httpClient;

  public GrizzlyHttpClientSchedulerTestCase(String serviceName) {
    super(serviceName);
  }

  @Before
  public void initialize() throws Exception {
    lockLatch = new Latch();
    previousTaskScheduled = new Latch();
    httpClient =
        service.getClientFactory().create(new HttpClientConfiguration.Builder().setName("http-client-scheduler").build());
  }

  private CountingExecutesScheduler createSelectorsScheduler() {
    return new CountingExecutesScheduler(basicSchedulerService.customScheduler(config()
        .withDirectRunCpuLightWhenTargetBusy(true)
        .withMaxConcurrentTasks(1)
        .withName("TestSelectorsScheduler")));
  }

  private SchedulerService createDecoratedSchedulerService() {
    return new SchedulerServiceAdapter(basicSchedulerService) {

      @Override
      public Scheduler customScheduler(SchedulerConfig config, int queueSize) {
        return selectorsScheduler.get();
      }
    };
  }

  @Override
  public SchedulerService getSchedulerService() {
    return decoratedSchedulerService.get();
  }

  @Test
  @Description("Start the pool with an scheduler running a blocked task. The selector pool should success to start sending one task to the queue.")
  public void testSchedulerWithNonFinishTask() throws ExecutionException, InterruptedException, TimeoutException {
    // The selectors pool has an old running task, which is blocked.
    Runnable oldTask = () -> {
      try {
        previousTaskScheduled.release();
        lockLatch.await();
      } catch (InterruptedException e) {
        fail("Fail initializing selector pool");
      }
    };
    selectorsScheduler.get().execute(oldTask);
    // Ensure the old task is actually running.
    previousTaskScheduled.await();

    // In another scheduler, we try to start the grizzly client. This will try to use the selectors scheduler to
    // run the selector runner task. That task should be enqueued because the old task is holding the only thread
    // in the selectorsScheduler.
    Scheduler startScheduler = basicSchedulerService.customScheduler(config()
        .withDirectRunCpuLightWhenTargetBusy(true)
        .withMaxConcurrentTasks(1)
        .withName("TestStartScheduler"));
    startScheduler.submit(() -> httpClient.start()).get(5, SECONDS);

    // At this point, the selectorsScheduler is still running the old task (it didn't finish), and the SelectorRunner
    // task didn't start yet.
    assertThat(selectorsScheduler.get().getStartedCommands(), is(1));
    assertThat(selectorsScheduler.get().getFinishedCommands(), is(0));

    // Release the lock allows the old task to finish.
    lockLatch.release();

    // Now, the old task finishes, and SelectorRunner has to start.
    PROBER.check(new JUnitLambdaProbe(() -> selectorsScheduler.get().getStartedCommands() == 2
        && selectorsScheduler.get().getFinishedCommands() == 1));
  }

  private static class CountingExecutesScheduler extends SchedulerAdapter {

    private final AtomicInteger startedCommands = new AtomicInteger();
    private final AtomicInteger finishedCommands = new AtomicInteger();

    public CountingExecutesScheduler(Scheduler schedulerDelegate) {
      super(schedulerDelegate);
    }

    @Override
    public void execute(Runnable command) {
      super.execute(() -> {
        startedCommands.incrementAndGet();
        try {
          command.run();
        } finally {
          finishedCommands.incrementAndGet();
        }
      });
    }

    public int getStartedCommands() {
      return startedCommands.get();
    }

    public int getFinishedCommands() {
      return finishedCommands.get();
    }
  }
}
