/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.service.http.impl.util;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.junit.rules.ExpectedException.none;

import java.io.IOException;
import java.util.concurrent.TimeoutException;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class TimedPipedInputStreamTestCase {

  @Rule
  public ExpectedException expectedException = none();

  @Test
  public void itBehavesInAFIFOWay() throws IOException {
    TimedPipedOutputStream out = new TimedPipedOutputStream();
    TimedPipedInputStream in = new TimedPipedInputStream(2, 10, MILLISECONDS, out);

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
    TimedPipedInputStream in = new TimedPipedInputStream(5, 10, MILLISECONDS, out);

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
  public void returnZeroAfterTimeoutWhenUsingBuffer() throws IOException {
    TimedPipedOutputStream out = new TimedPipedOutputStream();
    TimedPipedInputStream in = new TimedPipedInputStream(5, 10, MILLISECONDS, out);

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
  public void throwsExceptionAfterTimeoutWhenRequestingByte() throws IOException {
    TimedPipedOutputStream out = new TimedPipedOutputStream();
    TimedPipedInputStream in = new TimedPipedInputStream(5, 10, MILLISECONDS, out);

    expectedException.expect(instanceOf(IOException.class));
    expectedException.expectCause(instanceOf(TimeoutException.class));
    // noinspection ResultOfMethodCallIgnored, since it must throw an exception.
    in.read();
  }

  @Test
  public void throwsExceptionAfterTimeoutWhenRequestingByteAfterSomeBytes() throws IOException {
    TimedPipedOutputStream out = new TimedPipedOutputStream();
    TimedPipedInputStream in = new TimedPipedInputStream(5, 10, MILLISECONDS, out);

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

}
