/*
 * Copyright 2023 Salesforce, Inc. All rights reserved.
 */
package org.mule.service.http.impl.service.server.grizzly;

import java.io.IOException;
import java.io.InputStream;

import org.glassfish.grizzly.filterchain.FilterChainContext;
import org.glassfish.grizzly.http.HttpHeader;
import org.glassfish.grizzly.http.io.InputBuffer;

/**
 * {@link InputStream} to be used when the HTTP request has Transfer-Encoding: chunked or the content is not fully provided
 * because the message is too large.
 *
 * This {@link InputStream} implementation does a blocking read over the HTTP connection to read the next chunk when there is no
 * more data available.
 */
final class BlockingTransferInputStream extends InputStream {

  private final HttpHeader httpHeader;
  private final InputBuffer inputBuffer;
  private volatile String readNotAllowedReason = null;

  BlockingTransferInputStream(HttpHeader httpHeader, FilterChainContext ctx) {
    this.httpHeader = httpHeader;
    inputBuffer = new InputBuffer();
    inputBuffer.initialize(httpHeader, ctx);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public int read() throws IOException {
    throwIfReadingNotAllowedAndWouldBlock(1);
    return inputBuffer.readByte();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public int read(byte[] b) throws IOException {
    // We use only 1 regardless of the size of b because InputBuffer would return any amount of available data without
    // blocking, even if it is less than requested
    throwIfReadingNotAllowedAndWouldBlock(1);

    return read(b, 0, b.length);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public int read(byte[] b, int off, int len) throws IOException {
    // We use only 1 regardless of the size of b because InputBuffer would return any amount of available data without
    // blocking, even if it is less than requested
    throwIfReadingNotAllowedAndWouldBlock(1);

    return inputBuffer.read(b, off, len);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public long skip(long n) throws IOException {
    throwIfReadingNotAllowedAndWouldBlock(n);
    return inputBuffer.skip(n);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public int available() throws IOException {
    return inputBuffer.available();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void close() throws IOException {
    inputBuffer.close();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void mark(int readlimit) {
    inputBuffer.mark(readlimit);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void reset() throws IOException {
    inputBuffer.reset();
  }

  /**
   * This {@link InputStream} implementation supports marking.
   *
   * @return <code>true</code>
   */
  @Override
  public boolean markSupported() {
    return inputBuffer.markSupported();
  }

  /**
   * After calling this method, further reading operations that would block will throw an {@link IllegalStateException} with an
   * error message associated with the given reason.
   *
   * @param reason Reason for preventing further blocking reading operations.
   */
  public void preventFurtherBlockingReading(String reason) {
    this.readNotAllowedReason = reason;
  }

  private void throwIfReadingNotAllowedAndWouldBlock(long numBytes) {
    if (readNotAllowedReason != null && advancingWouldBlock(numBytes)) {
      throw new IllegalStateException("Reading from this stream is not allowed. Reason: " + readNotAllowedReason);
    }
  }

  private boolean advancingWouldBlock(long numBytes) {
    // There is knowledge of the internal logic of InputBuffer here, but it does not expose any method for checking if
    // a reading operation would require blocking, so we have to do the best we can here to simulate the same logic
    return inputBuffer.readyData() < numBytes && httpHeader.isExpectContent();
  }

}
