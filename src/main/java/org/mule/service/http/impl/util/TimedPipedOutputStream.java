/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.service.http.impl.util;

import java.io.IOException;
import java.io.OutputStream;

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
