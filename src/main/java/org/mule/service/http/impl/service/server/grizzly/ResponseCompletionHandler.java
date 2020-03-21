/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.service.http.impl.service.server.grizzly;

import static org.glassfish.grizzly.http.Method.HEAD;
import static org.glassfish.grizzly.http.Protocol.HTTP_1_0;
import static org.mule.runtime.api.i18n.I18nMessageFactory.createStaticMessage;
import static org.mule.runtime.api.util.Preconditions.checkArgument;
import static org.mule.runtime.http.api.HttpHeaders.Names.CONTENT_TYPE;

import org.mule.runtime.api.connection.ConnectionException;
import org.mule.runtime.api.exception.MuleRuntimeException;
import org.mule.runtime.http.api.domain.entity.HttpEntity;
import org.mule.runtime.http.api.domain.message.response.HttpResponse;
import org.mule.runtime.http.api.server.async.ResponseStatusCallback;

import java.io.IOException;

import org.glassfish.grizzly.Buffer;
import org.glassfish.grizzly.WriteResult;
import org.glassfish.grizzly.filterchain.FilterChainContext;
import org.glassfish.grizzly.http.HttpContent;
import org.glassfish.grizzly.http.HttpRequestPacket;
import org.glassfish.grizzly.http.HttpResponsePacket;
import org.glassfish.grizzly.http.HttpServerFilter;
import org.glassfish.grizzly.http.Protocol;
import org.glassfish.grizzly.memory.Buffers;

/**
 * {@link org.glassfish.grizzly.CompletionHandler}, responsible for asynchronous response writing
 */
public class ResponseCompletionHandler extends BaseResponseCompletionHandler {

  private final FilterChainContext ctx;
  private final ClassLoader ctxClassLoader;
  private final HttpResponsePacket httpResponsePacket;
  private final HttpContent httpResponseContent;
  private final Protocol protocol;
  private boolean isDone;
  private boolean contentSend;

  public ResponseCompletionHandler(final FilterChainContext ctx, ClassLoader ctxClassLoader,
                                   final HttpRequestPacket httpRequestPacket,
                                   final HttpResponse httpResponse, ResponseStatusCallback responseStatusCallback) {
    super(responseStatusCallback);
    checkArgument((!(httpResponse.getEntity().isStreaming())), "HTTP response entity cannot be stream based");
    this.ctx = ctx;
    this.ctxClassLoader = ctxClassLoader;
    this.protocol = httpRequestPacket.getProtocol();
    this.httpResponsePacket = buildHttpResponsePacket(httpRequestPacket, httpResponse);
    this.httpResponseContent = buildResponseContent(httpResponse);
  }

  public HttpContent buildResponseContent(final HttpResponse httpResponse) {
    final HttpEntity body = httpResponse.getEntity();
    Buffer grizzlyBuffer = null;
    if (body != null) {
      byte[] bytes;
      if (body.isComposed()) {
        try {
          bytes = HttpMultipartEncoder.toByteArray(body, httpResponsePacket.getHeader(CONTENT_TYPE));
        } catch (IOException e) {
          throw new MuleRuntimeException(createStaticMessage("Error sending multipart response"), e);
        }
      } else {
        try {
          bytes = body.getBytes();
        } catch (IOException e) {
          throw new MuleRuntimeException(createStaticMessage("Error sending response"), e);
        }
      }
      // Since we have the bytes, we'll try to default to Content-Length, unless it's HTTP 1.0 because we can only indicate
      // streaming by not having any headers set there
      if (!protocol.equals(HTTP_1_0) && !httpResponsePacket.isChunked() && !hasContentLength) {
        httpResponsePacket.setContentLength(bytes.length);
      }
      grizzlyBuffer = Buffers.wrap(ctx.getMemoryManager(), bytes);
    }

    HttpContent.Builder contentBuilder = HttpContent.builder(httpResponsePacket);
    // For some reason, grizzly tries to send Transfer-Encoding: chunk even if the content-length is set.
    if (hasContentLength || httpResponsePacket.getContentLength() > 0) {
      contentBuilder.last(true);
    }
    return contentBuilder.content(grizzlyBuffer).build();
  }

  /**
   * Start the sending the response asynchronously
   *
   * @throws java.io.IOException
   */
  public void start() throws IOException {
    sendResponse();
  }

  /**
   * Send the next part of the response
   *
   * @throws java.io.IOException
   */
  public void sendResponse() throws IOException {
    if (!contentSend) {
      contentSend = true;
      isDone = httpResponsePacket.getRequest().getMethod().equals(HEAD) || !httpResponsePacket.isChunked();
      ctx.write(httpResponseContent, this);
      return;
    }
    isDone = true;
    ctx.write(httpResponsePacket.httpTrailerBuilder().build(), this);
  }

  /**
   * Method gets called, when the message part was successfully sent.
   *
   * @param result the result
   */
  @Override
  public void completed(WriteResult result) {
    try {
      if (!isDone) {
        sendResponse();
      } else {
        doComplete();
      }
    } catch (IOException e) {
      failed(e);
    }
  }

  private void doComplete() {
    ctx.notifyDownstream(HttpServerFilter.RESPONSE_COMPLETE_EVENT);
    responseStatusCallback.responseSendSuccessfully();
    resume();
  }

  /**
   * The method will be called, when http message transferring was canceled
   */
  @Override
  public void cancelled() {
    super.cancelled();
    responseStatusCallback.responseSendFailure(new Exception("http response transferring cancelled"));
    resume();
  }

  /**
   * The method will be called, if http message transferring was failed.
   *
   * @param throwable the cause
   */
  @Override
  public void failed(Throwable throwable) {
    super.failed(throwable);
    callOnErrorSendingResponseIfPossible(ctx.getConnection().isOpen() ? throwable
        : new ConnectionException(CLIENT_CONNECTION_CLOSED_MESSAGE, throwable));
    resume();
  }

  /**
   * Resume the HttpRequestPacket processing
   */
  private void resume() {
    ctx.resume(ctx.getStopAction());
  }

  protected ClassLoader getCtxClassLoader() {
    return ctxClassLoader;
  }
}
