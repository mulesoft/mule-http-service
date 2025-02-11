/*
 * Copyright 2023 Salesforce, Inc. All rights reserved.
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.service.http.impl.util;

import static org.mule.service.http.impl.AllureConstants.HttpFeature.HttpStory.RESPONSES;
import static org.mule.service.http.impl.AllureConstants.HttpFeature.HttpStory.STREAMING;

import static java.util.concurrent.Executors.newSingleThreadExecutor;
import static java.util.concurrent.TimeUnit.HOURS;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.fail;
import static org.junit.rules.ExpectedException.none;

import org.mule.runtime.api.util.concurrent.Latch;
import org.mule.tck.junit4.AbstractMuleTestCase;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import io.qameta.allure.Issue;
import io.qameta.allure.Stories;
import io.qameta.allure.Story;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

@Stories({@Story(RESPONSES), @Story(STREAMING)})
public class TimedPipedInputStreamTestCase extends AbstractMuleTestCase {

  @Rule
  public ExpectedException expectedException = none();

  private final ExecutorService writerExecutor = newSingleThreadExecutor();

  private final AtomicInteger timesCallbackWasCalled = new AtomicInteger();
  private final Runnable onSpaceCallback = timesCallbackWasCalled::incrementAndGet;

  @Test
  public void itBehavesInAFIFOWayWhenWritingBytes() throws IOException {
    TimedPipedOutputStream out = new TimedPipedOutputStream();
    TimedPipedInputStream in = new TimedPipedInputStream(2, 10, MILLISECONDS, out, onSpaceCallback);

    out.write(1);
    out.write(2);
    assertThat(in.read(), is(1));
    assertThat(in.read(), is(2));

    out.write(3);
    assertThat(in.read(), is(3));

    out.write(4);
    out.write(5);
    assertThat(in.read(), is(4));
    assertThat(in.read(), is(5));
  }

  @Test
  public void itBehavesInAFIFOWayWhenWritingBuffers() throws IOException {
    TimedPipedOutputStream out = new TimedPipedOutputStream();
    TimedPipedInputStream in = new TimedPipedInputStream(5, 10, MILLISECONDS, out, onSpaceCallback);

    byte[] receiveBuf = new byte[2];

    out.write(new byte[] {1, 2, 3});

    assertThat(in.read(receiveBuf), is(2));
    assertThat(receiveBuf[0], is((byte) 1));
    assertThat(receiveBuf[1], is((byte) 2));

    assertThat(in.read(receiveBuf), is(1));
    assertThat(receiveBuf[0], is((byte) 3));

    out.write(new byte[] {4, 5, 6, 7, 8});
    assertThat(in.read(receiveBuf), is(2));
    assertThat(in.read(receiveBuf), is(2));
    assertThat(in.read(receiveBuf), is(1));
    assertThat(receiveBuf[0], is((byte) 8));
  }

  @Test
  @Issue("MULE-19232")
  public void returnZeroAfterTimeoutWhenUsingBuffer() throws IOException {
    TimedPipedOutputStream out = new TimedPipedOutputStream();
    TimedPipedInputStream in = new TimedPipedInputStream(5, 10, MILLISECONDS, out, onSpaceCallback);

    byte[] receiveBuf = new byte[2];

    // Ring buffer is empty, return 0.
    assertThat(in.read(receiveBuf), is(0));

    out.write(new byte[] {1, 2, 3});

    assertThat(in.read(receiveBuf), is(2));
    assertThat(in.read(receiveBuf), is(1));

    // Ring buffer is empty again, return 0.
    assertThat(in.read(receiveBuf), is(0));
  }

  @Test
  @Issue("MULE-19232")
  public void throwsExceptionAfterTimeoutWhenRequestingByte() throws IOException {
    TimedPipedOutputStream out = new TimedPipedOutputStream();
    TimedPipedInputStream in = new TimedPipedInputStream(5, 10, MILLISECONDS, out, onSpaceCallback);

    expectedException.expect(instanceOf(IOException.class));
    expectedException.expectCause(instanceOf(TimeoutException.class));
    // noinspection ResultOfMethodCallIgnored, since it must throw an exception.
    in.read();
  }

  @Test
  @Issue("MULE-19232")
  public void throwsExceptionAfterTimeoutWhenRequestingByteAfterSomeBytes() throws IOException {
    TimedPipedOutputStream out = new TimedPipedOutputStream();
    TimedPipedInputStream in = new TimedPipedInputStream(5, 10, MILLISECONDS, out, onSpaceCallback);

    out.write(new byte[] {1, 2, 3});

    // Reads are ok when data is available.
    assertThat(in.read(), is(1));
    assertThat(in.read(), is(2));
    assertThat(in.read(), is(3));

    // After the data is gone, read operation throws exception.
    expectedException.expect(instanceOf(IOException.class));
    expectedException.expectCause(instanceOf(TimeoutException.class));
    // noinspection ResultOfMethodCallIgnored, since it must throw an exception.
    in.read();
  }

  @Test
  public void readerAndWriterInDifferentThreadsWithAPayloadThatDoesNotFitIntoTheBuffer()
      throws IOException, InterruptedException {
    TimedPipedOutputStream out = new TimedPipedOutputStream();
    TimedPipedInputStream in = new TimedPipedInputStream(5, 10, MILLISECONDS, out, onSpaceCallback);
    String testData = "Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor incididunt ut " +
        "labore et dolore magna aliqua. Ut enim ad minim veniam, quis nostrud exercitation ullamco " +
        "laboris nisi ut aliquip ex ea commodo consequat. Duis aute irure dolor in reprehenderit in " +
        "voluptate velit esse cillum dolore eu fugiat nulla pariatur. Excepteur sint occaecat cupidatat" +
        " non proident, sunt in culpa qui officia deserunt mollit anim id est laborum.";
    Latch dataWritten = new Latch();

    writerExecutor.submit(() -> {
      try {
        out.write(testData.getBytes());
        dataWritten.release();
      } catch (IOException e) {
        fail(e.getMessage());
      }
    });

    StringBuilder sb = new StringBuilder();
    while (sb.length() != testData.length()) {
      byte[] buffer = new byte[64];
      int currentRead = in.read(buffer);
      sb.append(new String(buffer, 0, currentRead));
    }

    dataWritten.await();
    assertThat(sb.toString(), is(testData));
  }

  @Test
  @Issue("W-17627284")
  public void callbackIsCalledWhenSpaceIsGenerated() throws IOException {
    String payloadThatFillsTheBuffer = "Lorem ipsum dolor sit amet";

    TimedPipedOutputStream out = new TimedPipedOutputStream();
    TimedPipedInputStream in = new TimedPipedInputStream(payloadThatFillsTheBuffer.length(), 10, HOURS, out, onSpaceCallback);
    out.write(payloadThatFillsTheBuffer.getBytes());

    assertThat(timesCallbackWasCalled.get(), is(0));

    byte[] readBuf = new byte[2];

    // The first read actually generates space in the buffer.
    in.read(readBuf);
    assertThat(timesCallbackWasCalled.get(), is(1));

    // Subsequent reads generate more space in the buffer, but the buffer wasn't full anymore
    in.read(readBuf);
    assertThat(timesCallbackWasCalled.get(), is(1));
    in.read(readBuf);
    assertThat(timesCallbackWasCalled.get(), is(1));

    // Fill the buffer again...
    out.write(payloadThatFillsTheBuffer.getBytes(), 0, 6);

    // And now the first read is generating space again
    in.read(readBuf);
    assertThat(timesCallbackWasCalled.get(), is(2));
  }
}
