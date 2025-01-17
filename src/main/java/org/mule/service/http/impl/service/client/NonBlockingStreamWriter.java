/*
 * Copyright 2023 Salesforce, Inc. All rights reserved.
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.service.http.impl.service.client;

import static java.lang.Boolean.getBoolean;
import static java.lang.Math.min;
import static java.lang.Thread.currentThread;
import static java.lang.Thread.sleep;

import static org.slf4j.LoggerFactory.getLogger;
import static org.slf4j.MDC.getCopyOfContextMap;

import org.mule.service.http.impl.service.util.ThreadContext;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import org.slf4j.Logger;

/**
 * Writes the data passed via the non-blocking method {@link #addDataToWrite(OutputStream, byte[], Supplier)} to the specified
 * stream, only if there is available space. If no space available, it saves a task to try later. When it tried to execute all the
 * pending writes, and it couldn't, then it sleeps a certain period passed via constructor (default is
 * {@link #DEFAULT_TIME_TO_SLEEP_WHEN_COULD_NOT_WRITE_MILLIS}). This is done to avoid an unnecessarily high CPU consumption.
 */
public class NonBlockingStreamWriter implements Runnable {

  private static final boolean KILL_SWITCH = getBoolean("mule.http.client.responseStreaming.nonBlockingWriter");

  private static final Logger LOGGER = getLogger(NonBlockingStreamWriter.class);
  private static final int DEFAULT_TIME_TO_SLEEP_WHEN_COULD_NOT_WRITE_MILLIS = 100;

  private final AtomicBoolean isStopped = new AtomicBoolean(false);
  private final BlockingQueue<InternalWriteTask> tasks = new LinkedBlockingQueue<>();
  private final int timeToSleepWhenCouldNotWriteMillis;
  private final boolean isEnabled;

  public NonBlockingStreamWriter(int timeToSleepWhenCouldNotWriteMillis, boolean isEnabled) {
    this.timeToSleepWhenCouldNotWriteMillis = timeToSleepWhenCouldNotWriteMillis;
    this.isEnabled = isEnabled;
  }

  public NonBlockingStreamWriter() {
    this(DEFAULT_TIME_TO_SLEEP_WHEN_COULD_NOT_WRITE_MILLIS, KILL_SWITCH);
  }

  public boolean isEnabled() {
    return isEnabled;
  }

  /**
   * Tries to write from <code>dataToWrite</code> to <code>destinationStream</code>, as many bytes as the
   * <code>availableSpace</code> supplier says it's possible to write. If the supplier returns <code>0</code>, it schedules a task
   * to try later.
   * 
   * @param destinationStream where the data has to be written.
   * @param dataToWrite       the data to write.
   * @param availableSpace    a supplier that says how many bytes can be written to the stream without blocking.
   * @return a {@link CompletableFuture} that will be completed when all the data was written, or when an exception occurs.
   */
  public CompletableFuture<Void> addDataToWrite(OutputStream destinationStream,
                                                byte[] dataToWrite,
                                                Supplier<Integer> availableSpace) {

    InternalWriteTask internalWriteTask = new InternalWriteTask(destinationStream, dataToWrite, availableSpace);
    boolean couldCompleteSync = internalWriteTask.execute();
    if (!couldCompleteSync) {
      tasks.add(internalWriteTask);
    }
    return internalWriteTask.getFuture();
  }

  @Override
  public void run() {
    while (!isStopped.get()) {
      try {
        boolean couldWriteSomething = writeWhateverPossible();

        if (!couldWriteSomething && !isStopped.get()) {
          LOGGER.trace("Giving some time to the other threads to consume from pipes...");
          sleep(timeToSleepWhenCouldNotWriteMillis);
        }
      } catch (InterruptedException e) {
        if (!isStopped.get()) {
          LOGGER.warn("Non blocking writer thread was interrupted before it was stopped. It will resume the execution", e);
        }
      }
    }
  }

  /**
   * Signals the writer to stop writing. It can also be stopped by interrupting the thread where it's running.
   */
  public void stop() {
    isStopped.set(true);
  }

  /**
   * Iterates all the pending write tasks and executes them.
   * 
   * @return <code>true</code> if it could write at least one byte, or <code>false</code> otherwise.
   * @throws InterruptedException if the thread was interrupted.
   */
  private boolean writeWhateverPossible() throws InterruptedException {
    List<InternalWriteTask> tasksWithPendingData = new ArrayList<>(tasks.size());
    boolean couldWriteSomething = false;

    InternalWriteTask task = tasks.poll(100, TimeUnit.MILLISECONDS);
    while (task != null) {
      int remainingBeforeExecute = task.remaining();
      boolean couldComplete = task.execute();
      int remainingAfterExecute = task.remaining();
      if (!couldComplete) {
        tasksWithPendingData.add(task);
      }
      if (remainingAfterExecute < remainingBeforeExecute) {
        couldWriteSomething = true;
      }
      task = tasks.poll(100, TimeUnit.MILLISECONDS);
    }

    tasks.addAll(tasksWithPendingData);
    return couldWriteSomething;
  }

  private static final class InternalWriteTask {

    private static final AtomicInteger idGenerator = new AtomicInteger(0);

    private final OutputStream destinationStream;
    private final byte[] dataToWrite;
    private final int totalBytesToWrite;
    private final Supplier<Integer> availableSpace;
    private final CompletableFuture<Void> toCompleteWhenAllDataIsWritten;
    private final int id;
    private final Map<String, String> callerMDC;
    private final ClassLoader callerTCCL;

    private int alreadyWritten;

    public InternalWriteTask(OutputStream destinationStream, byte[] dataToWrite, Supplier<Integer> availableSpace) {
      this.id = idGenerator.getAndIncrement();
      this.destinationStream = destinationStream;
      this.availableSpace = availableSpace;
      this.toCompleteWhenAllDataIsWritten = new CompletableFuture<>();
      this.totalBytesToWrite = dataToWrite.length;
      this.dataToWrite = dataToWrite;
      this.alreadyWritten = 0;

      this.callerMDC = getCopyOfContextMap();
      this.callerTCCL = currentThread().getContextClassLoader();
    }

    public int remaining() {
      return totalBytesToWrite - alreadyWritten;
    }

    public boolean execute() {
      try (ThreadContext threadContext = new ThreadContext(callerTCCL, callerMDC)) {
        int remainingBytes = totalBytesToWrite - alreadyWritten;
        int bytesToWriteInThisExecution = min(availableSpace.get(), remainingBytes);

        while (bytesToWriteInThisExecution > 0) {
          try {
            destinationStream.write(dataToWrite, alreadyWritten, bytesToWriteInThisExecution);
          } catch (Exception e) {
            toCompleteWhenAllDataIsWritten.completeExceptionally(e);
            LOGGER.trace("Error on write (id: {})", id, e);
            return true;
          }
          alreadyWritten += bytesToWriteInThisExecution;

          remainingBytes = totalBytesToWrite - alreadyWritten;
          bytesToWriteInThisExecution = min(availableSpace.get(), remainingBytes);
        }

        if (alreadyWritten == totalBytesToWrite) {
          LOGGER.trace("Fully written (id: {})", id);
          toCompleteWhenAllDataIsWritten.complete(null);
          return true;
        }

        if (bytesToWriteInThisExecution == -1) {
          LOGGER.trace("Destination stream closed (id: {})", id);
          toCompleteWhenAllDataIsWritten.completeExceptionally(new IOException("Pipe closed"));
          return true;
        }

        LOGGER.trace("Written bytes: {}/{} (id: {})", alreadyWritten, totalBytesToWrite, id);
        return false;
      }
    }

    public CompletableFuture<Void> getFuture() {
      return toCompleteWhenAllDataIsWritten;
    }
  }
}
