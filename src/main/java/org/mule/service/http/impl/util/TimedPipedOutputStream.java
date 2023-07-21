/*
 * Copyright 2023 Salesforce, Inc. All rights reserved.
 */
package org.mule.service.http.impl.util;

import java.io.IOException;
import java.io.OutputStream;

/**
 * Output stream to be used in conjunction with a {@link TimedPipedOutputStream}.
 *
 * @since 1.6.0 and 1.5.11.
 */
public class TimedPipedOutputStream extends OutputStream {

  private TimedPipedInputStream sink;

  public void connect(TimedPipedInputStream sink) {
    this.sink = sink;
  }

  @Override
  public void write(int b) throws IOException {
    sink.receive(b);
  }

  @Override
  public void write(byte[] b) throws IOException {
    sink.receive(b);
  }

  @Override
  public void write(byte[] b, int off, int len) throws IOException {
    sink.receive(b, off, len);
  }

  @Override
  public void close() throws IOException {
    sink.receivedLast();
  }
}
