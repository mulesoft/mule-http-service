/*
 * Copyright 2023 Salesforce, Inc. All rights reserved.
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.service.http.impl.functional.client;

import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.mule.service.http.impl.service.client.CustomTimeoutThrottleRequestFilter;

import com.ning.http.client.AsyncHandler;
import com.ning.http.client.HttpResponseBodyPart;
import com.ning.http.client.HttpResponseHeaders;
import com.ning.http.client.HttpResponseStatus;
import com.ning.http.client.Request;
import com.ning.http.client.filter.FilterContext;
import com.ning.http.client.filter.FilterException;
import io.qameta.allure.Issue;
import org.junit.Test;

@Issue("W-14686211")
public class CustomTimeoutThrottleRequestFilterTestCase {

  @Test
  public void aFilterContextIsAvailableAfterAnOnThrowableCallWhenMaxConnectionsIsOne() throws Exception {
    CustomTimeoutThrottleRequestFilter testCustomTimeoutThrottleRequestFilter = new TestCustomTimeoutThrottleRequestFilter(1);
    FilterContext context = testCustomTimeoutThrottleRequestFilter.filter(getFilterContext());
    context.getAsyncHandler().onThrowable(mock(Throwable.class));
  }

  private static FilterContext getFilterContext() {
    Request request = mock(Request.class);
    FilterContext filterContext = mock(FilterContext.class);
    when(filterContext.getRequest()).thenReturn(request);
    when(request.getRequestTimeout()).thenReturn(100);
    return filterContext;
  }

  private static class TestCustomTimeoutThrottleRequestFilter extends CustomTimeoutThrottleRequestFilter {

    public TestCustomTimeoutThrottleRequestFilter(int maxConnections) {
      super(maxConnections);
    }

    @Override
    protected AsyncHandler resolveAsyncHandler(FilterContext ctx) {
      return new AsyncHandler() {

        @Override
        public void onThrowable(Throwable throwable) {
          try {
            filter(getFilterContext());
          } catch (FilterException e) {
            fail(e.getMessage());
          }
        }

        @Override
        public STATE onBodyPartReceived(HttpResponseBodyPart httpResponseBodyPart) throws Exception {
          return null;
        }

        @Override
        public STATE onStatusReceived(HttpResponseStatus httpResponseStatus) throws Exception {
          return null;
        }

        @Override
        public STATE onHeadersReceived(HttpResponseHeaders httpResponseHeaders) throws Exception {
          return null;
        }

        @Override
        public Object onCompleted() throws Exception {
          return null;
        }
      };
    }
  }
}
