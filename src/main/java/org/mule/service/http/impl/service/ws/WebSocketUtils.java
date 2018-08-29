/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.service.http.impl.service.ws;

import static java.lang.System.arraycopy;
import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.glassfish.grizzly.utils.Futures.completable;

import java.io.InputStream;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

import org.glassfish.grizzly.GrizzlyFuture;

public final class WebSocketUtils {

  public static final int DEFAULT_DATA_FRAME_SIZE = 8 * 1024;

  public static CompletableFuture<Void> streamDataFrame(InputStream content, DataFrameEmitter emitter) {
    return streamDataFrame(content, DEFAULT_DATA_FRAME_SIZE, emitter);
  }

  public static CompletableFuture<Void> streamDataFrame(InputStream content, int frameSize, DataFrameEmitter emitter) {
    byte[] readBuffer = new byte[frameSize];
    byte[] writeBuffer = new byte[frameSize];

    int read;
    int write = 0;
    boolean streaming = false;
    AtomicReference<Throwable> error = new AtomicReference<>(null);

    try {
      while (error.get() == null && (read = content.read(readBuffer, 0, readBuffer.length)) != -1) {
        if (write > 0 && error.get() == null) {
          emitter.stream(writeBuffer, 0, writeBuffer.length, false).whenComplete((v, e) -> {
            if (e != null) {
              error.set(e);
            }
          });
          streaming = true;
        }
        arraycopy(readBuffer, 0, writeBuffer, 0, read);
        write = read;
      }

      if (error.get() != null) {
        return failedFuture(error.get());
      }

      if (write == 0) {
        return completedFuture(null);
      }

      // because a bug in grizzly we need to create a byte array with the exact length
      if (write < writeBuffer.length) {
        byte[] exactSize = writeBuffer;
        writeBuffer = new byte[write];
        arraycopy(exactSize, 0, writeBuffer, 0, write);
      }

      if (error.get() != null) {
        return failedFuture(error.get());
      }

      if (streaming) {
        return emitter.stream(writeBuffer, 0, write, true);
      } else {
        return emitter.send(writeBuffer, 0, write);
      }
    } catch (Throwable t) {
      return failedFuture(t);
    }
  }

  public static <T> CompletableFuture<Void> asVoid(CompletableFuture<T> future) {
    CompletableFuture<Void> vf = new CompletableFuture<>();
    future.whenComplete((v, e) -> {
      if (e != null) {
        vf.completeExceptionally(e);
      } else {
        vf.complete(null);
      }
    });

    return vf;
  }

  public static <T> CompletableFuture<Void> asVoid(GrizzlyFuture<T> future) {
    return asVoid(completable(future));
  }

  public static <T> CompletableFuture<T> failedFuture(Throwable t) {
    CompletableFuture<T> future = new CompletableFuture<>();
    future.completeExceptionally(t);

    return future;
  }
}
