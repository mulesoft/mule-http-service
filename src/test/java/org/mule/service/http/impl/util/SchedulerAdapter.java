/*
 * Copyright 2023 Salesforce, Inc. All rights reserved.
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.service.http.impl.util;

import org.mule.runtime.api.scheduler.Scheduler;

import java.util.Collection;
import java.util.List;
import java.util.TimeZone;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class SchedulerAdapter implements Scheduler {

  protected Scheduler schedulerDelegate;

  public SchedulerAdapter(Scheduler schedulerDelegate) {
    this.schedulerDelegate = schedulerDelegate;
  }


  @Override
  public ScheduledFuture<?> scheduleWithCronExpression(Runnable runnable, String s) {
    return schedulerDelegate.scheduleWithCronExpression(runnable, s);
  }

  @Override
  public ScheduledFuture<?> scheduleWithCronExpression(Runnable runnable, String s, TimeZone timeZone) {
    return schedulerDelegate.scheduleWithCronExpression(runnable, s, timeZone);
  }

  @Override
  public void stop() {
    schedulerDelegate.stop();
  }

  @Override
  public String getName() {
    return schedulerDelegate.getName();
  }

  @Override
  public ScheduledFuture<?> schedule(Runnable command, long delay, TimeUnit unit) {
    return schedulerDelegate.schedule(command, delay, unit);
  }

  @Override
  public <V> ScheduledFuture<V> schedule(Callable<V> callable, long delay, TimeUnit unit) {
    return schedulerDelegate.schedule(callable, delay, unit);
  }

  @Override
  public ScheduledFuture<?> scheduleAtFixedRate(Runnable command, long initialDelay, long period, TimeUnit unit) {
    return schedulerDelegate.scheduleAtFixedRate(command, initialDelay, period, unit);
  }

  @Override
  public ScheduledFuture<?> scheduleWithFixedDelay(Runnable command, long initialDelay, long delay, TimeUnit unit) {
    return schedulerDelegate.scheduleWithFixedDelay(command, initialDelay, delay, unit);
  }

  @Override
  public void shutdown() {
    schedulerDelegate.shutdown();
  }

  @Override
  public List<Runnable> shutdownNow() {
    return schedulerDelegate.shutdownNow();
  }

  @Override
  public boolean isShutdown() {
    return schedulerDelegate.isShutdown();
  }

  @Override
  public boolean isTerminated() {
    return schedulerDelegate.isTerminated();
  }

  @Override
  public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
    return schedulerDelegate.awaitTermination(timeout, unit);
  }

  @Override
  public <T> Future<T> submit(Callable<T> task) {
    return schedulerDelegate.submit(task);
  }

  @Override
  public <T> Future<T> submit(Runnable task, T result) {
    return schedulerDelegate.submit(task, result);
  }

  @Override
  public Future<?> submit(Runnable task) {
    return schedulerDelegate.submit(task);
  }

  @Override
  public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks) throws InterruptedException {
    return schedulerDelegate.invokeAll(tasks);
  }

  @Override
  public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit)
      throws InterruptedException {
    return schedulerDelegate.invokeAll(tasks, timeout, unit);
  }

  @Override
  public <T> T invokeAny(Collection<? extends Callable<T>> tasks) throws InterruptedException, ExecutionException {
    return schedulerDelegate.invokeAny(tasks);
  }

  @Override
  public <T> T invokeAny(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit)
      throws InterruptedException, ExecutionException, TimeoutException {
    return schedulerDelegate.invokeAny(tasks, timeout, unit);
  }

  @Override
  public void execute(Runnable command) {
    schedulerDelegate.execute(command);
  }
}
