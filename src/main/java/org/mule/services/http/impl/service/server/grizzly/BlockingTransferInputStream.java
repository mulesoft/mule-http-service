/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.services.http.impl.service.server.grizzly;

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

  private final InputBuffer inputBuffer;

  BlockingTransferInputStream(HttpHeader httpHeader, FilterChainContext ctx) {
    inputBuffer = new InputBuffer();
    inputBuffer.initialize(httpHeader, ctx);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public int read() throws IOException {
    return inputBuffer.readByte();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public int read(byte[] b) throws IOException {
    return inputBuffer.read(b, 0, b.length);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public int read(byte[] b, int off, int len) throws IOException {
    return inputBuffer.read(b, off, len);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public long skip(long n) throws IOException {
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

}
