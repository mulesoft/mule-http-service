/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.service.http.impl.util;

import static java.lang.Integer.min;
import static java.lang.System.arraycopy;
import static java.lang.System.nanoTime;
import static java.lang.Thread.currentThread;

import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Input stream which is blocking only during a specified timeout. It requires to be connected with a
 * {@link TimedPipedOutputStream}.
 *
 * @since 1.6.0 and 1.5.11.
 */
public class TimedPipedInputStream extends InputStream {

  // Internal data.
  private byte[] ringBuffer;
  private final int ringBufferSize;

  // Callback to notify that there is available space in the pipe.
  private final Runnable spaceAvailableCallback;

  // Read timeout in nanoseconds.
  private final long timeoutNanos;

  // Index from the first byte available (if length > 0).
  private CircularInteger head;

  // Bytes available in the buffer.
  private int length = 0;

  private boolean closedByWriter = false;
  private boolean closedByReader = false;

  public TimedPipedInputStream(int bufferSize, long timeout, TimeUnit timeUnit, TimedPipedOutputStream origin,
                               Runnable spaceAvailableCallback) {
    this.ringBuffer = new byte[bufferSize];
    this.ringBufferSize = bufferSize;
    this.timeoutNanos = timeUnit.toNanos(timeout);
    this.head = new CircularInteger(ringBufferSize, 0);
    this.spaceAvailableCallback = spaceAvailableCallback;
    origin.connect(this);
  }

  @Override
  public synchronized int read() throws IOException {
    try {
      int bytesAvailable = awaitDataAvailable();
      if (bytesAvailable > 0) {
        byte returnValue = ringBuffer[head.get()];
        head.increase();
        length -= 1;
        notifyAll();
        return returnValue & 0xff;
      } else if (closedByWriter) {
        notifyAll();
        return -1;
      } else {
        throw new IOException(new TimeoutException("Timeout while reading from piped stream using a blocking read() method"));
      }
    } catch (InterruptedException e) {
      currentThread().interrupt();
      throw new IOException(e);
    }
  }

  /**
   * See {@link InputStream}, but it may return 0 if no byte has been read after the specified timeout.
   * 
   * @param b Destination buffer.
   * @return the total number of bytes read into the buffer, <code>0</code> if there is no available data after the timeout is
   *         reached, or <code>-1</code> if there is no more data because the end of the stream has been reached.
   * @throws IOException
   */
  @Override
  public int read(byte[] b) throws IOException {
    return read(b, 0, b.length);
  }

  /**
   * See {@link InputStream}, but it may return 0 if no byte has been read after the specified timeout.
   * 
   * @param b Destination buffer.
   * @return the total number of bytes read into the buffer, <code>0</code> if there is no available data after the timeout is
   *         reached, or <code>-1</code> if there is no more data because the end of the stream has been reached.
   * @throws IOException
   */
  @Override
  public synchronized int read(byte[] b, int off, int len) throws IOException {
    if (len == 0) {
      return 0;
    }
    try {
      boolean wasFull = length == ringBufferSize;
      int bytesToCopy = min(awaitDataAvailable(), len);
      if (bytesToCopy == 0 && closedByWriter) {
        notifyAll();
        return -1;
      }

      // As it's a ring buffer, we could need two copies.
      int firstCopy = min(ringBufferSize - head.get(), bytesToCopy);
      arraycopy(ringBuffer, head.get(), b, off, firstCopy);
      if (firstCopy < bytesToCopy) {
        arraycopy(ringBuffer, 0, b, off + firstCopy, bytesToCopy - firstCopy);
      }
      head.increase(bytesToCopy);

      // There is space in the buffer, notify writers.
      length -= bytesToCopy;
      notifyAll();
      if (wasFull && 0 < bytesToCopy) {
        spaceAvailableCallback.run();
      }
      return bytesToCopy;
    } catch (InterruptedException e) {
      currentThread().interrupt();
      throw new IOException(e);
    }
  }

  /**
   * Gets the number of bytes available to be read.
   *
   * @return The number of available bytes.
   */
  @Override
  public synchronized int available() {
    return length;
  }

  /**
   * Notifies all waiting threads that the last byte of data has been received.
   */
  synchronized void receivedLast() {
    closedByWriter = true;
    notifyAll();
  }

  private synchronized int awaitDataAvailable() throws InterruptedException, IOException {
    long initialNanos = nanoTime();
    long finalNanos = initialNanos + timeoutNanos;

    while (length <= 0 && nanoTime() < finalNanos && !closedByReader) {
      if (closedByWriter) {
        return 0;
      }
      wait(100);
    }
    if (closedByReader) {
      throw new IOException("Pipe closed");
    }

    return length;
  }

  synchronized void receive(int b) throws IOException {
    awaitSpace();
    ringBuffer[(head.get() + length) % ringBufferSize] = (byte) (b & 0xff);
    length += 1;

    // Now there is data available to be read.
    notifyAll();
  }

  void receive(byte[] bytes) throws IOException {
    receive(bytes, 0, bytes.length);
  }

  synchronized void receive(byte[] bytes, int off, int len) throws IOException {
    if (len <= 0) {
      return;
    }

    int bytesToCopy = min(awaitSpace(), len);
    CircularInteger destinationIndex = head.plus(length);

    // As it's a ring buffer, we could need two copies.
    int firstCopyLength = min(ringBufferSize - destinationIndex.get(), bytesToCopy);
    arraycopy(bytes, off, ringBuffer, destinationIndex.get(), firstCopyLength);
    if (firstCopyLength < bytesToCopy) {
      arraycopy(bytes, off + firstCopyLength, ringBuffer, 0, bytesToCopy - firstCopyLength);
    }

    // There is data in the buffer, notify readers.
    length += bytesToCopy;
    notifyAll();

    receive(bytes, off + bytesToCopy, len - bytesToCopy);
  }

  /**
   * Waits until there is space in the ring buffer. The read timeout is NOT related to this method.
   *
   * @return The available space in bytes.
   * @throws IOException if the waiting thread is interrupted.
   */
  private synchronized int awaitSpace() throws IOException {
    try {
      while (length == ringBufferSize && !closedByWriter && !closedByReader) {
        wait(100);
      }
      if (closedByWriter || closedByReader) {
        throw new IOException("Pipe closed");
      }
    } catch (InterruptedException e) {
      currentThread().interrupt();
      throw new IOException(e);
    }

    return ringBufferSize - length;
  }

  @Override
  public synchronized void close() throws IOException {
    closedByReader = true;
    notifyAll();
  }

  public synchronized boolean isClosed() {
    return closedByReader || closedByWriter;
  }

  private class CircularInteger {

    private int cap;
    private int value;

    CircularInteger(int cap, int value) {
      this.cap = cap;
      this.value = value % cap;
    }

    int get() {
      return value;
    }

    void increase() {
      this.value += 1;
      this.value %= this.cap;
    }

    void increase(int increment) {
      this.value += increment;
      this.value %= this.cap;
    }

    CircularInteger plus(int increment) {
      return new CircularInteger(this.cap, this.value + increment);
    }
  }
}
