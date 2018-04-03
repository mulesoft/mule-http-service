/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.service.http.impl.service.client;

import static java.util.concurrent.TimeUnit.MILLISECONDS;

import com.ning.http.client.AsyncHandler;
import com.ning.http.client.HttpResponseBodyPart;
import com.ning.http.client.HttpResponseHeaders;
import com.ning.http.client.HttpResponseStatus;
import com.ning.http.client.filter.FilterContext;
import com.ning.http.client.filter.FilterException;
import com.ning.http.client.filter.RequestFilter;

import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A {@link RequestFilter} that throttles requests and blocks when the number of permits is reached,
 * waiting for the response to arrive before executing the next request.
 *
 * This is based on {@code com.ning.http.client.extra.ThrottleRequestFilter} from Async Http Client, but uses the request timeout
 * from each request and throttles based on the configured maximum connections allowed.
 */
public class CustomTimeoutThrottleRequestFilter implements RequestFilter {

  private final static Logger logger = LoggerFactory.getLogger(CustomTimeoutThrottleRequestFilter.class);
  private final Semaphore available;

  public CustomTimeoutThrottleRequestFilter(int maxConnections) {
    available = new Semaphore(maxConnections, true);
  }

  @Override
  public FilterContext filter(FilterContext ctx) throws FilterException {
    try {
      int timeout = ctx.getRequest().getRequestTimeout();
      if (logger.isDebugEnabled()) {
        logger.debug("Current available connections: {}, Maximum wait time: {}", available.availablePermits(), timeout);
      }
      if (!available.tryAcquire(timeout, MILLISECONDS)) {
        logger.debug("Rejecting request {} in AsyncHandler {}", ctx.getRequest(), ctx.getAsyncHandler());
        throw new FilterException("Connection limit exceeded, cannot process request");
      }
    } catch (InterruptedException e) {
      logger.debug("Interrupted request {} in AsyncHandler {}", ctx.getRequest(), ctx.getAsyncHandler());
      throw new FilterException("Interrupted request");
    }

    return new FilterContext.FilterContextBuilder(ctx).asyncHandler(new AsyncHandlerWrapper(ctx.getAsyncHandler())).build();
  }

  private class AsyncHandlerWrapper<T> implements AsyncHandler<T> {

    private final AsyncHandler<T> asyncHandler;
    private final AtomicBoolean complete = new AtomicBoolean(false);

    public AsyncHandlerWrapper(AsyncHandler<T> asyncHandler) {
      this.asyncHandler = asyncHandler;
    }

    private void complete() {
      if (complete.compareAndSet(false, true)) {
        available.release();
      }
      if (logger.isDebugEnabled()) {
        logger.debug("Current available connections after processing: {}", available.availablePermits());
      }
    }

    @Override
    public void onThrowable(Throwable t) {
      try {
        asyncHandler.onThrowable(t);
      } finally {
        complete();
      }
    }

    @Override
    public STATE onBodyPartReceived(HttpResponseBodyPart bodyPart) throws Exception {
      return asyncHandler.onBodyPartReceived(bodyPart);
    }

    @Override
    public STATE onStatusReceived(HttpResponseStatus responseStatus) throws Exception {
      return asyncHandler.onStatusReceived(responseStatus);
    }

    @Override
    public STATE onHeadersReceived(HttpResponseHeaders headers) throws Exception {
      return asyncHandler.onHeadersReceived(headers);
    }

    @Override
    public T onCompleted() throws Exception {
      try {
        return asyncHandler.onCompleted();
      } finally {
        complete();
      }
    }
  }
}
