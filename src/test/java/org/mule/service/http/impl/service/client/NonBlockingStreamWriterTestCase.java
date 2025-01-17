/*
 * Copyright 2023 Salesforce, Inc. All rights reserved.
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.service.http.impl.service.client;

import static org.mule.functional.junit4.matchers.ThrowableMessageMatcher.hasMessage;

import static java.lang.System.currentTimeMillis;
import static java.lang.Thread.sleep;
import static java.util.Arrays.stream;
import static java.util.concurrent.Executors.newSingleThreadExecutor;
import static java.util.stream.Collectors.toCollection;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

import org.mule.service.http.impl.service.util.ThreadContext;
import org.mule.tck.junit4.AbstractMuleTestCase;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import io.qameta.allure.Issue;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.MDC;

@Issue("W-17048606")
public class NonBlockingStreamWriterTestCase extends AbstractMuleTestCase {

  private static final int TEST_TIME_TO_SLEEP_WHEN_COULD_NOT_WRITE_MILLIS = 50;
  private static final byte[] SOME_DATA = "Some data to write".getBytes();
  private static final ExecutorService executorService = newSingleThreadExecutor();

  private NonBlockingStreamWriter nonBlockingStreamWriter;

  @Before
  public void setUp() {
    // not scheduling always to test the sync test cases
    nonBlockingStreamWriter = new NonBlockingStreamWriter(TEST_TIME_TO_SLEEP_WHEN_COULD_NOT_WRITE_MILLIS, true);
  }

  @After
  public void tearDown() {
    nonBlockingStreamWriter.stop();
  }

  @Test
  public void writesIfAvailableSpace() throws ExecutionException, InterruptedException {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    nonBlockingStreamWriter.addDataToWrite(out, SOME_DATA, new SequenceProvider(SOME_DATA.length + 1)).get();
    assertThat(out.toByteArray(), is(SOME_DATA));
  }

  @Test
  public void partiallyWritesIfNotEnoughSpace() {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    CompletableFuture<Void> future =
        nonBlockingStreamWriter.addDataToWrite(out, SOME_DATA, new SequenceProvider(SOME_DATA.length - 1));
    assertThat(future.isDone(), is(false));
    assertThat(out.toByteArray().length, is(SOME_DATA.length - 1));
  }

  @Test
  public void writesAllProgressivelyWhenSpaceIsGenerated() throws ExecutionException, InterruptedException {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    nonBlockingStreamWriter.addDataToWrite(out, SOME_DATA, new SequenceProvider(SOME_DATA.length - 5, 5)).get();
    assertThat(out.toByteArray(), is(SOME_DATA));
  }

  @Test
  public void failsWhenStreamIsClosed() {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    ExecutionException exception =
        assertThrows(ExecutionException.class,
                     () -> nonBlockingStreamWriter.addDataToWrite(out, SOME_DATA, new SequenceProvider(-1)).get());
    Throwable cause = exception.getCause();
    assertThat(cause, instanceOf(IOException.class));
    assertThat(cause, hasMessage(containsString("Pipe closed")));
  }

  @Test
  public void writesAllAsync() throws ExecutionException, InterruptedException {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    Supplier<Integer> sequenceWithAZeroInTheMiddle = new SequenceProvider(SOME_DATA.length - 5, 0, 5);
    CompletableFuture<Void> future = nonBlockingStreamWriter.addDataToWrite(out, SOME_DATA, sequenceWithAZeroInTheMiddle);
    assertThat("The writer is not scheduled anywhere yet, so the future shouldn't be completed",
               future.isDone(), is(false));

    executorService.submit(nonBlockingStreamWriter);

    // now it has to be completed...
    future.get();

    assertThat(out.toByteArray(), is(SOME_DATA));
  }

  @Test
  public void failureAsync() {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    Supplier<Integer> sequenceWithAZeroInTheMiddle = new SequenceProvider(SOME_DATA.length - 5, 0, -1);
    CompletableFuture<Void> future = nonBlockingStreamWriter.addDataToWrite(out, SOME_DATA, sequenceWithAZeroInTheMiddle);
    assertThat("The writer is not scheduled anywhere yet, so the future shouldn't be completed",
               future.isDone(), is(false));

    executorService.submit(nonBlockingStreamWriter);

    ExecutionException exception = assertThrows(ExecutionException.class, future::get);
    Throwable cause = exception.getCause();
    assertThat(cause, instanceOf(IOException.class));
    assertThat(cause, hasMessage(containsString("Pipe closed")));
  }

  @Test
  public void streamWithoutSpaceManyTimesSleeps() throws ExecutionException, InterruptedException {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    Supplier<Integer> sequenceWithThreeZeroesInTheMiddle = new SequenceProvider(SOME_DATA.length - 5, 0, 0, 0, 5);
    CompletableFuture<Void> future = nonBlockingStreamWriter.addDataToWrite(out, SOME_DATA, sequenceWithThreeZeroesInTheMiddle);
    assertThat("The writer is not scheduled anywhere yet, so the future shouldn't be completed",
               future.isDone(), is(false));

    long millisBeforeSchedule = currentTimeMillis();
    executorService.submit(nonBlockingStreamWriter);

    // now it has to be completed...
    future.get();
    long millisAfterComplete = currentTimeMillis();
    int elapsedMillis = (int) (millisAfterComplete - millisBeforeSchedule);
    assertThat("We returned 0 three times, so the writer should have slept twice at this point",
               elapsedMillis, is(greaterThanOrEqualTo(2 * TEST_TIME_TO_SLEEP_WHEN_COULD_NOT_WRITE_MILLIS)));

    assertThat(out.toByteArray(), is(SOME_DATA));
  }

  @Test
  public void writesAllProgressivelyAsync() throws ExecutionException, InterruptedException {
    executorService.submit(nonBlockingStreamWriter);
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    nonBlockingStreamWriter.addDataToWrite(out, SOME_DATA, new SequenceProvider(SOME_DATA.length - 5, 0, 0, 1, 0, 1, 1, 0, 1, 1))
        .get();
    assertThat(out.toByteArray(), is(SOME_DATA));
  }

  @Test
  public void ioExceptionOnWriteIsCaughtAndPropagatedToTheFuture() throws IOException {
    IOException expectedException = new IOException("Expected!!");
    OutputStream throwing = mock(OutputStream.class);
    doThrow(expectedException).when(throwing).write(any(byte[].class), anyInt(), anyInt());

    CompletableFuture<Void> future =
        nonBlockingStreamWriter.addDataToWrite(throwing, SOME_DATA, new SequenceProvider(SOME_DATA.length));
    ExecutionException exception = assertThrows(ExecutionException.class, future::get);
    assertThat(exception.getCause(), is(expectedException));
  }

  @Test
  public void runtimeExceptionOnWriteIsCaughtAndPropagatedToTheFuture() throws IOException {
    RuntimeException expectedException = new RuntimeException("Expected!!");
    OutputStream throwing = mock(OutputStream.class);
    doThrow(expectedException).when(throwing).write(any(byte[].class), anyInt(), anyInt());

    CompletableFuture<Void> future =
        nonBlockingStreamWriter.addDataToWrite(throwing, SOME_DATA, new SequenceProvider(SOME_DATA.length));
    ExecutionException exception = assertThrows(ExecutionException.class, future::get);
    assertThat(exception.getCause(), is(expectedException));
  }

  @Test
  public void interruptTheThreadDoesntInterruptTheWriterIfNotStopped() throws InterruptedException {
    NonBlockingStreamWriter writer = new NonBlockingStreamWriter();
    Thread threadOutsideTheStaticExecutor = new Thread(writer);
    threadOutsideTheStaticExecutor.start();
    threadOutsideTheStaticExecutor.interrupt();

    // Give it some time to finish if it will (it should not)
    threadOutsideTheStaticExecutor.join(500);
    assertThat(threadOutsideTheStaticExecutor.isAlive(), is(true));

    writer.stop();
    threadOutsideTheStaticExecutor.join();
    assertThat(threadOutsideTheStaticExecutor.isAlive(), is(false));
  }

  @Test
  public void interruptTheThreadAfterStopWillInterruptTheSleep() throws InterruptedException {
    int ridiculouslyBigSleepMillis = Integer.MAX_VALUE;
    NonBlockingStreamWriter writer = new NonBlockingStreamWriter(ridiculouslyBigSleepMillis, true);
    Thread threadOutsideTheStaticExecutor = new Thread(writer);
    threadOutsideTheStaticExecutor.start();

    // "Ensure" that the thread is sleeping
    sleep(500);
    writer.stop();

    // We told the writer to stop, but it's still sleeping
    threadOutsideTheStaticExecutor.join(500);
    assertThat(threadOutsideTheStaticExecutor.isAlive(), is(true));

    // If we interrupt the writer now, it will be joined without any issue
    threadOutsideTheStaticExecutor.interrupt();
    threadOutsideTheStaticExecutor.join();
    assertThat(threadOutsideTheStaticExecutor.isAlive(), is(false));
  }

  @Test
  public void writeOperationIsExecutedWithSameThreadContext() throws ExecutionException, InterruptedException {
    executorService.submit(nonBlockingStreamWriter);
    OutputStreamSavingThreadContext out = new OutputStreamSavingThreadContext();

    Map<String, String> mockMdc = new HashMap<>();
    mockMdc.put("Key1", "Value1");
    mockMdc.put("Key2", "Value2");
    ClassLoader mockClassLoader = mock(ClassLoader.class);
    try (ThreadContext tc = new ThreadContext(mockClassLoader, mockMdc)) {
      // the 0 of the SequenceProvider forces the last write to happen in the writer's thread
      nonBlockingStreamWriter.addDataToWrite(out, SOME_DATA, new SequenceProvider(SOME_DATA.length - 5, 0, 5)).get();
    }

    assertThat(out.getClassLoaderOnLastWrite(), is(mockClassLoader));
    assertThat(out.getMDCOnLastWrite(), is(mockMdc));
  }

  private static class SequenceProvider implements Supplier<Integer> {

    private final Queue<Integer> sequence;

    SequenceProvider(Integer... sequence) {
      this.sequence = stream(sequence).collect(toCollection(LinkedList::new));
    }

    @Override
    public Integer get() {
      if (sequence.isEmpty()) {
        return 0;
      }
      return sequence.remove();
    }
  }

  private static class OutputStreamSavingThreadContext extends OutputStream {

    private final AtomicReference<ClassLoader> classLoaderOnWrite = new AtomicReference<>();
    private final AtomicReference<Map<String, String>> mdcOnWrite = new AtomicReference<>();

    @Override
    public void write(int b) throws IOException {
      mdcOnWrite.set(MDC.getCopyOfContextMap());
      classLoaderOnWrite.set(Thread.currentThread().getContextClassLoader());
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
      mdcOnWrite.set(MDC.getCopyOfContextMap());
      classLoaderOnWrite.set(Thread.currentThread().getContextClassLoader());
      super.write(b, off, len);
    }

    public ClassLoader getClassLoaderOnLastWrite() {
      return classLoaderOnWrite.get();
    }

    public Map<String, String> getMDCOnLastWrite() {
      return mdcOnWrite.get();
    }
  }
}
