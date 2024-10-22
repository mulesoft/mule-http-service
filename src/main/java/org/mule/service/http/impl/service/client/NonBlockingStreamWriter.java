/*
 * Copyright 2023 Salesforce, Inc. All rights reserved.
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.service.http.impl.service.client;

import static java.lang.Math.min;
import static java.lang.Thread.currentThread;

import static org.slf4j.LoggerFactory.getLogger;

import java.io.IOException;
import java.io.OutputStream;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import org.slf4j.Logger;

public class NonBlockingStreamWriter implements Runnable {

  private static final Logger LOGGER = getLogger(NonBlockingStreamWriter.class);

  private final AtomicBoolean isStopped = new AtomicBoolean(false);
  private BlockingQueue<InternalWriteTask> tasks = new LinkedBlockingQueue<>();

  @Override
  public void run() {
    try {
      while (!isStopped.get()) {
        boolean couldWriteSomething = writeWhateverPossible();
        if (!couldWriteSomething) {
          LOGGER.trace("Giving some time to the other threads to consume from pipes...");
          Thread.sleep(100);
        }
      }
    } catch (InterruptedException e) {
      currentThread().interrupt();
    }
  }

  private boolean writeWhateverPossible() throws InterruptedException {
    BlockingQueue<InternalWriteTask> tasksWithPendingData = new LinkedBlockingQueue<>();
    boolean couldCompleteSomething = false;

    InternalWriteTask task = tasks.poll(100, TimeUnit.MILLISECONDS);
    while (task != null) {
      boolean couldComplete = task.execute();
      if (couldComplete) {
        couldCompleteSomething = true;
      } else {
        tasksWithPendingData.add(task);
      }
      task = tasks.poll(100, TimeUnit.MILLISECONDS);
    }

    tasks = tasksWithPendingData;
    return couldCompleteSomething;
  }

  public CompletableFuture<Void> addDataToWrite(OutputStream destinationStream,
                                                byte[] dataToWrite,
                                                Supplier<Integer> availableSpace) {

    InternalWriteTask internalWriteTask = new InternalWriteTask(destinationStream, dataToWrite, availableSpace);
    tasks.add(internalWriteTask);
    return internalWriteTask.getFuture();
  }

  public void stop() {
    isStopped.set(true);
  }

  private static final class InternalWriteTask {

    private static final AtomicInteger idGenerator = new AtomicInteger(0);

    private final OutputStream destinationStream;
    private final byte[] dataToWrite;
    private final int totalBytesToWrite;
    private final Supplier<Integer> availableSpace;
    private final CompletableFuture<Void> toCompleteWhenAllDataIsWritten;
    private final int id;

    private int alreadyWritten;

    public InternalWriteTask(OutputStream destinationStream, byte[] dataToWrite, Supplier<Integer> availableSpace) {
      this.id = idGenerator.getAndIncrement();

      LOGGER.debug("Adding data to write (id: {})", id);

      this.destinationStream = destinationStream;
      this.availableSpace = availableSpace;
      this.toCompleteWhenAllDataIsWritten = new CompletableFuture<>();
      this.totalBytesToWrite = dataToWrite.length;
      this.dataToWrite = dataToWrite;
      this.alreadyWritten = 0;
    }

    public boolean execute() {
      int remainingBytes = totalBytesToWrite - alreadyWritten;
      int bytesToWriteInThisExecution = min(availableSpace.get(), remainingBytes);

      while (bytesToWriteInThisExecution > 0) {
        try {
          destinationStream.write(dataToWrite, alreadyWritten, bytesToWriteInThisExecution);
        } catch (Exception e) {
          toCompleteWhenAllDataIsWritten.completeExceptionally(e);
          LOGGER.debug("Error on write (id: {})", id, e);
          return true;
        }
        alreadyWritten += bytesToWriteInThisExecution;

        remainingBytes = totalBytesToWrite - alreadyWritten;
        bytesToWriteInThisExecution = min(availableSpace.get(), remainingBytes);
      }

      if (alreadyWritten == totalBytesToWrite) {
        LOGGER.debug("Fully written (id: {})", id);
        toCompleteWhenAllDataIsWritten.complete(null);
        return true;
      }

      if (bytesToWriteInThisExecution == -1) {
        LOGGER.debug("Destination stream closed (id: {})", id);
        toCompleteWhenAllDataIsWritten.completeExceptionally(new IOException("Pipe closed"));
        return true;
      }

      LOGGER.debug("Written bytes: {}/{} (id: {})", alreadyWritten, totalBytesToWrite, id);
      return false;
    }

    public CompletableFuture<Void> getFuture() {
      return toCompleteWhenAllDataIsWritten;
    }
  }
}
