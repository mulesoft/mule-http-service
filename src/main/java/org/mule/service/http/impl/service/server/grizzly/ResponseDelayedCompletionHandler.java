/*
 * Copyright 2023 Salesforce, Inc. All rights reserved.
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.service.http.impl.service.server.grizzly;

import static java.lang.Integer.MAX_VALUE;
import static java.lang.System.arraycopy;
import static org.glassfish.grizzly.http.HttpServerFilter.RESPONSE_COMPLETE_EVENT;
import static org.mule.runtime.api.i18n.I18nMessageFactory.createStaticMessage;
import org.mule.runtime.api.exception.DefaultMuleException;
import org.mule.runtime.http.api.domain.message.response.HttpResponse;
import org.mule.runtime.http.api.server.async.ResponseStatusCallback;

import java.io.IOException;
import java.io.Writer;
import java.nio.charset.Charset;

import org.glassfish.grizzly.Buffer;
import org.glassfish.grizzly.WriteResult;
import org.glassfish.grizzly.filterchain.FilterChainContext;
import org.glassfish.grizzly.http.HttpContent;
import org.glassfish.grizzly.http.HttpRequestPacket;
import org.glassfish.grizzly.http.HttpResponsePacket;
import org.glassfish.grizzly.memory.MemoryManager;

final class ResponseDelayedCompletionHandler extends BaseResponseCompletionHandler {

  private final MemoryManager memoryManager;
  protected final FilterChainContext ctx;
  private final ClassLoader ctxClassLoader;
  private final HttpResponsePacket httpResponsePacket;
  private final ResponseStatusCallback responseStatusCallback;

  ResponseDelayedCompletionHandler(FilterChainContext ctx, ClassLoader ctxClassLoader, HttpRequestPacket request,
                                   HttpResponse httpResponse,
                                   ResponseStatusCallback responseStatusCallback) {
    this.ctx = ctx;
    this.ctxClassLoader = ctxClassLoader;
    httpResponsePacket = buildHttpResponsePacket(request, httpResponse);
    memoryManager = ctx.getConnection().getTransport().getMemoryManager();
    this.responseStatusCallback = responseStatusCallback;
  }

  public void start() {
    // TODO - MULE-15051: Analyse how to either support this entirely or using a Connection close approach
    httpResponsePacket.setContentLength(MAX_VALUE);
    httpResponsePacket.setChunked(false);
    final HttpContent content = httpResponsePacket.httpContentBuilder().build();

    ctx.write(content, this);
  }

  public Writer buildWriter(Charset encoding) {
    return new Writer() {

      private final StringBuilder stringBuilder = new StringBuilder();

      @Override
      public void write(char[] cbuf, int off, int len) throws IOException {
        stringBuilder.append(cbuf, off, len);
      }

      @Override
      public void flush() throws IOException {
        byte[] bytes = stringBuilder.toString().getBytes(encoding);
        final Buffer buffer = memoryManager.allocate(bytes.length);
        final byte[] bufferByteArray = buffer.array();
        final int offset = buffer.arrayOffset();

        arraycopy(bytes, 0, bufferByteArray, offset, bytes.length);
        stringBuilder.setLength(0);
        ctx.write(buffer, ResponseDelayedCompletionHandler.this);
      }

      @Override
      public void close() throws IOException {
        ctx.write(httpResponsePacket.httpTrailerBuilder().build(), ResponseDelayedCompletionHandler.this);
        responseStatusCallback.responseSendSuccessfully();
        ctx.notifyDownstream(RESPONSE_COMPLETE_EVENT);
        resume();
      }
    };
  }

  @Override
  public void completed(WriteResult result) {
    // Nothing to do, completion will be associated to writer being closed
  }

  /**
   * The method will be called, when file transferring was canceled
   */
  @Override
  public void cancelled() {
    super.cancelled();
    responseStatusCallback
        .responseSendFailure(new DefaultMuleException(createStaticMessage("HTTP response sending task was cancelled")));
    resume();
  }

  /**
   * The method will be called, if file transferring was failed.
   *
   * @param throwable the cause
   */
  @Override
  public void failed(Throwable throwable) {
    super.failed(throwable);
    responseStatusCallback.onErrorSendingResponse(throwable);
    resume();
  }

  /**
   * Resume the HttpRequestPacket processing
   */
  private void resume() {
    ctx.resume(ctx.getStopAction());
  }

  @Override
  protected ClassLoader getCtxClassLoader() {
    return ctxClassLoader;
  }
}
