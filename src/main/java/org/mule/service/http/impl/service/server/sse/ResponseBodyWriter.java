/*
 * Copyright 2023 Salesforce, Inc. All rights reserved.
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.service.http.impl.service.server.sse;

import org.mule.runtime.http.api.server.async.ResponseStatusCallback;

import java.io.IOException;
import java.io.Writer;
import java.nio.charset.Charset;

import org.glassfish.grizzly.filterchain.FilterChainContext;

/**
 * Implementation of {@link Writer} returned by
 * {@link org.mule.runtime.http.api.server.async.HttpResponseReadyCallback#startResponse} so the caller can write the response
 * body. On {@link #write}, it accumulates the written data in a {@link StringBuilder}, and on {@link #flush()}, it sends the data
 * to the Netty's {@link ChannelHandlerContext ctx}. On {@link #close()}, it writes an empty "last" content to Netty, and marks
 * the {@link ResponseStatusCallback callback} as completed successfully.
 */
public class ResponseBodyWriter extends Writer {

  // TODO: What?
  private final FilterChainContext ctx;
  private final Charset encoding;
  private final ResponseStatusCallback callback;
  private final StringBuilder stringBuilder;

  public ResponseBodyWriter(FilterChainContext ctx, Charset encoding, ResponseStatusCallback callback) {
    this.ctx = ctx;
    this.encoding = encoding;
    this.callback = callback;
    this.stringBuilder = new StringBuilder();
  }

  @Override
  public void write(char[] charBuffer, int off, int len) throws IOException {
    stringBuilder.append(charBuffer, off, len);
  }

  @Override
  public void flush() {
    byte[] bytes = stringBuilder.toString().getBytes(encoding);
    stringBuilder.setLength(0);
    // TODO: Actual write to grizzly connection
  }

  @Override
  public void close() throws IOException {
    // TODO: Finish grizzly writing...
    callback.responseSendSuccessfully();
  }
}
