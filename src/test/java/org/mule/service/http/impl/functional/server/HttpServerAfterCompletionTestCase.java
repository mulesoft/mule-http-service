/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.service.http.impl.functional.server;

import io.qameta.allure.Issue;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.entity.InputStreamEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mule.runtime.http.api.domain.message.response.HttpResponse;
import org.mule.runtime.http.api.domain.request.HttpRequestContext;

import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.apache.http.HttpVersion.HTTP_1_1;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

public class HttpServerAfterCompletionTestCase extends AbstractHttpServerTestCase {

  private static final String PATH = "/workAfterResponse";

  private AsyncRequestHandler afterResponseRequestHandler;

  public HttpServerAfterCompletionTestCase(String serviceToLoad) {
    super(serviceToLoad);
  }

  @Rule
  public ExpectedException expectedException = ExpectedException.none();

  @Before
  public void setUp() throws Exception {
    setUpServer();

    // Registers a request handler that will delegate to a test-case-local handler after having sent the response
    server.addRequestHandler(PATH, (requestContext, responseCallback) -> {
      responseCallback.responseReady(HttpResponse.builder().build(), new IgnoreResponseStatusCallback());
      afterResponseRequestHandler.handleRequest(requestContext);
    });
  }

  @Override
  protected String getServerName() {
    return "after-completion-test";
  }

  @Test
  @Issue("MULE-19951")
  public void failsGracefullyWhenReadAfterCompletion() throws Exception {
    // It is necessary to use a big streamed payload to make it fail, so that it doesn't fit inside the internal buffers.
    AlphabetGeneratorInputStream infiniteAlphabet = new AlphabetGeneratorInputStream(1 << 20);

    afterResponseRequestHandler = new AsyncRequestHandler((requestContext) -> {
      // We'll just consume the request body and compare it against the original stream.
      // We expect it to fail at some point, when buffered data is exhausted.
      InputStream inputStream = requestContext.getRequest().getEntity().getContent();
      assertThat(infiniteAlphabet.equals(inputStream), is(true));
    });

    // Just sends a request with a chunked transfer encoding
    try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
      HttpPut httpPut = new HttpPut(getUri());
      httpPut.setProtocolVersion(HTTP_1_1);

      httpPut.setEntity(new InputStreamEntity(infiniteAlphabet));
      httpClient.execute(httpPut).close();
    }

    // We expect it to have failed with a helpful exception
    expectedException.expectCause(instanceOf(IllegalStateException.class));
    expectedException.expectMessage("Reading from this stream is not allowed. Reason: Response already sent");
    afterResponseRequestHandler.test();
  }

  @Test
  @Issue("MULE-19951")
  public void whenDataBufferedAndReadAfterCompletionSucceeds() throws Exception {
    // It is necessary to use a big streamed payload to make it fail, so that it doesn't fit inside the internal buffers.
    AlphabetGeneratorInputStream infiniteAlphabet = new AlphabetGeneratorInputStream(2048);

    afterResponseRequestHandler = new AsyncRequestHandler((requestContext) -> {
      // We'll just consume the request body and compare it against the original stream.
      // We expect it to fail at some point, when buffered data is exhausted.
      InputStream inputStream = requestContext.getRequest().getEntity().getContent();
      assertThat(infiniteAlphabet.equals(inputStream), is(true));
    });

    // Just sends a request with a chunked transfer encoding
    try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
      HttpPut httpPut = new HttpPut(getUri());
      httpPut.setProtocolVersion(HTTP_1_1);

      httpPut.setEntity(new InputStreamEntity(infiniteAlphabet));
      httpClient.execute(httpPut).close();
    }

    // We expect it to be able to read the full request until the end because it should be buffered
    afterResponseRequestHandler.test();
  }

  private String getUri() {
    return "http://localhost:" + port.getValue() + PATH;
  }

  /**
   * An {@link InputStream} which will generate characters from a-z in order until a specified limit is reached. This class is
   * used for generating big streamed payloads for the requests.
   */
  private static class AlphabetGeneratorInputStream extends InputStream {

    private static final int ALPHABET_SIZE = 'z' - 'a';
    private long offset = 0;
    private final long limit;

    public AlphabetGeneratorInputStream(long limit) {
      this.limit = limit;
    }

    @Override
    public int read() throws IOException {
      if (offset >= limit) {
        return -1;
      }
      return charFromOffset(offset++);
    }

    public long getLimit() {
      return limit;
    }

    public boolean equals(InputStream inputStream) throws IOException {
      long offset = 0;
      while (true) {
        int c = inputStream.read();
        if (c < 0) {
          break;
        }
        if (c != charFromOffset(offset++)) {
          return false;
        }
      }
      return offset == getLimit();
    }

    private int charFromOffset(long offset) {
      return (int) (offset % ALPHABET_SIZE) + 'a';
    }
  }

  /**
   * In order to replicate some issues, the handling of the request needs to be done asynchronously so that the handler has
   * actually finished. This class is a helper for executing a simplified request handler (something that consumes an
   * {@link HttpRequestContext}) asynchronously. It will take care of storing any raised exceptions and rethrowing them when
   * calling {@link #test()}.
   */
  private static class AsyncRequestHandler {

    public interface ThrowingConsumer<T> {

      void accept(T input) throws Exception;
    }

    private Future<?> future;
    private final ExecutorService es = Executors.newSingleThreadExecutor();
    private final ThrowingConsumer<HttpRequestContext> throwingConsumer;
    private final CountDownLatch handleRequestCalledLatch = new CountDownLatch(1);

    public AsyncRequestHandler(ThrowingConsumer<HttpRequestContext> throwingConsumer) {
      this.throwingConsumer = throwingConsumer;
    }

    public void handleRequest(HttpRequestContext requestContext) {
      future = es.submit(() -> {
        throwingConsumer.accept(requestContext);
        return null;
      });
      handleRequestCalledLatch.countDown();
    }

    public void test() throws Exception {
      handleRequestCalledLatch.await();
      future.get();
    }
  }
}
