/*
 * Copyright 2023 Salesforce, Inc. All rights reserved.
 */
package org.mule.service.http.impl.functional;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import org.mule.runtime.api.util.concurrent.Latch;

import java.io.IOException;
import java.io.InputStream;

/**
 * Custom {@link InputStream} that will fill the internal buffers and over to a specific size, then wait on a {@link Latch} before
 * completing. The behaviour guarantees that an {@link org.mule.runtime.http.api.server.HttpServer} will flush the response status
 * line but hold the body off until specified which is useful to test streaming scenarios.
 */
public class FillAndWaitStream extends InputStream {

  // Use a payload bigger than the default server and client buffer sizes (8 and 10 KB, respectively)
  public static final int RESPONSE_SIZE = 14 * 1024;
  private static final int WAIT_TIMEOUT = 5000;

  private int sent = 0;
  private Latch latch;

  public FillAndWaitStream(Latch latch) {
    this.latch = latch;
  }

  @Override
  public int read() throws IOException {
    if (sent < RESPONSE_SIZE) {
      sent++;
      return 42;
    } else {
      try {
        latch.await(WAIT_TIMEOUT, MILLISECONDS);
      } catch (InterruptedException e) {
        // Do nothing
      }
      return -1;
    }
  }
}
