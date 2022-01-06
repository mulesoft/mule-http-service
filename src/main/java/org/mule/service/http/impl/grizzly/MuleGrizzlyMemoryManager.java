/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.service.http.impl.grizzly;

import org.glassfish.grizzly.Buffer;
import org.glassfish.grizzly.memory.ByteBufferWrapper;
import org.glassfish.grizzly.memory.HeapMemoryManager;
import org.glassfish.grizzly.memory.MemoryManager;
import org.glassfish.grizzly.monitoring.MonitoringConfig;
import org.mule.runtime.api.memory.provider.ByteBufferProvider;

import java.nio.ByteBuffer;

/**
 *
 */
public class MuleGrizzlyMemoryManager implements MemoryManager {

  private final HeapMemoryManager memoryManager = new HeapMemoryManager();

  private static ByteBufferProvider byteBufferProvider;

  public static void setMuleMemoryManager(ByteBufferProvider bufferProvider) {
    byteBufferProvider = bufferProvider;
  }

  @Override
  public Buffer allocate(int i) {
    return new MuleBuffer(byteBufferProvider.allocate(i));
  }

  @Override
  public Buffer allocateAtLeast(int i) {
    return new MuleBuffer(byteBufferProvider.allocateAtLeast(i));
  }

  @Override
  public Buffer reallocate(Buffer buffer, int i) {
    if (buffer instanceof MuleBuffer) {
      return new ByteBufferWrapper(byteBufferProvider.reallocate(((MuleBuffer) byteBufferProvider).getVisibleByteBuffer(), i));
    }

    throw new IllegalArgumentException("The buffer must be an instance of MuleBuffer");
  }

  @Override
  public void release(Buffer buffer) {
    if (buffer instanceof MuleBuffer) {
      byteBufferProvider.release(((MuleBuffer) buffer).getVisibleByteBuffer());
    }
  }

  @Override
  public boolean willAllocateDirect(int i) {
    return false;
  }

  @Override
  public MonitoringConfig getMonitoringConfig() {
    return memoryManager.getMonitoringConfig();
  }

  private static Buffer wrap(ByteBuffer byteBuffer) {
    return new MuleBuffer(byteBuffer);
  }

  private static class MuleBuffer extends ByteBufferWrapper {

    public MuleBuffer(ByteBuffer byteBuffer) {
      super(byteBuffer);
    }

    public ByteBuffer getVisibleByteBuffer() {
      return visible;
    }
  }
}
