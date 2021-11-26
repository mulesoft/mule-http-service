/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.service.http.impl.service.server.grizzly;


import org.glassfish.grizzly.ReadResult;
import org.glassfish.grizzly.filterchain.FilterChainContext;
import org.glassfish.grizzly.http.HttpContent;
import org.glassfish.grizzly.http.HttpHeader;
import org.glassfish.grizzly.memory.Buffers;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mule.tck.junit4.AbstractMuleTestCase;

import java.io.IOException;
import java.util.Iterator;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.Arrays.stream;
import static org.glassfish.grizzly.memory.MemoryManager.DEFAULT_MEMORY_MANAGER;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class BlockingTransferInputStreamTestCase extends AbstractMuleTestCase {

  final FilterChainContext fccMock = mock(FilterChainContext.class);

  @Rule
  public ExpectedException expectedException = ExpectedException.none();

  @Before
  public void setUp() {}

  @Test
  public void basicRead() throws IOException {
    final BlockingTransferInputStream inputStream = createStreamWithChunks("Some content");

    assertThat(inputStream.read(), is((int) 'S'));

    byte[] data = new byte[3];
    assertThat(inputStream.read(data), is(data.length));
    assertThat(new String(data), is("ome"));

    byte[] remainingData = new byte[100];
    assertThat(inputStream.read(remainingData, 0, 100), is(" content".length()));
    assertThat(new String(remainingData, 0, " content".length()), is(" content"));

    // Should return -1 when no more data is available
    assertThat(inputStream.read(data), is(-1));
    assertThat(inputStream.read(remainingData), is(-1));
    assertThat(inputStream.read(remainingData, 0, 100), is(-1));
  }

  @Test
  public void basicReadMultipleChunks() throws IOException {
    final BlockingTransferInputStream inputStream = createStreamWithChunks("Some content", ", ", "Other content");

    assertThat(inputStream.read(), is((int) 'S'));

    byte[] data = new byte[3];
    assertThat(inputStream.read(data), is(data.length));
    assertThat(new String(data), is("ome"));

    byte[] remainingData = new byte[100];
    assertThat(inputStream.read(remainingData, 0, 100), is(" content".length()));
    assertThat(new String(remainingData, 0, " content".length()), is(" content"));

    assertThat(inputStream.read(remainingData, 0, 100), is(", ".length()));
    assertThat(new String(remainingData, 0, ", ".length()), is(", "));

    assertThat(inputStream.read(remainingData, 0, 100), is("Other content".length()));
    assertThat(new String(remainingData, 0, "Other content".length()), is("Other content"));

    // Should return -1 when no more data is available
    assertThat(inputStream.read(data), is(-1));
    assertThat(inputStream.read(remainingData), is(-1));
    assertThat(inputStream.read(remainingData, 0, 100), is(-1));
  }

  @Test
  public void whenReadAfterNotAllowedReturnsIllegalStateException() throws IOException {
    final BlockingTransferInputStream inputStream = createStreamWithChunks("Some content", ", ", "More content");

    verify(fccMock, times(0)).read();

    assertThat(inputStream.read(), is((int) 'S'));

    inputStream.preventFurtherBlockingReading("for the sake of testing");

    // It should not fail here, since data is readily available on the internal buffer
    byte[] data = new byte[10];
    assertThat(inputStream.read(data), is(data.length));
    assertThat(new String(data), is("ome conten"));

    // Should be able to get whatever is available in the buffer, even if more data was requested.
    byte[] remainingData = new byte[100];
    assertThat(inputStream.read(remainingData, 0, 100), is("t".length()));
    assertThat(new String(remainingData, 0, "t".length()), is("t"));

    // At this point it should fail because it will try to get more data which would require an additional blocking read
    expectedException.expect(instanceOf(IllegalStateException.class));
    inputStream.read(remainingData, 0, 100);
    inputStream.read(remainingData);
    inputStream.read();

    // Control test to see that no blocking read was performed
    verify(fccMock, times(0)).read();
  }

  @Test
  public void whenContentIsBufferedAndReadAfterNotAllowedSucceeds() throws IOException {
    final BlockingTransferInputStream inputStream = createStreamWithChunks("Some content");

    verify(fccMock, times(0)).read();

    inputStream.preventFurtherBlockingReading("for the sake of testing");

    // It should not fail here, since data is readily available on the internal buffer
    byte[] data = new byte[100];
    assertThat(inputStream.read(data), is("Some content".length()));
    assertThat(new String(data, 0, "Some content".length()), is("Some content"));

    // At this point it should return -1 since there is no more content
    assertThat(inputStream.read(data, 0, 100), is(-1));
    assertThat(inputStream.read(data), is(-1));
    assertThat(inputStream.read(), is(-1));

    // Control test to see that no blocking read was performed
    verify(fccMock, times(0)).read();
  }

  private BlockingTransferInputStream createStreamWithChunks(String... chunks) throws IOException {
    // This method is subject to get broken if the internal implementation of Grizzly's InputBuffer changes

    final Iterator<String> chunksIterator = stream(chunks).iterator();

    // Creates the first content to be returned as message (while consuming the first element of the iterator)
    final HttpContent httpContent = httpContentFromString(chunksIterator.next(), chunksIterator.hasNext());

    // Creates another iterator with the remaining chunks converted into ReadResult, suitable for read responses
    final Iterator<? extends ReadResult<HttpContent, ?>> readResults =
        Stream.generate(() -> readResultFromString(chunksIterator.next(), chunksIterator.hasNext())).limit(chunks.length - 1)
            .collect(Collectors.toList()).iterator();

    // The first chunk is returned from getMessage
    when(fccMock.getMessage()).thenReturn(httpContent);

    // Subsequent chunks come from read
    when(fccMock.read()).thenAnswer(invocation -> readResults.next());

    // It is important to also update the isExpectContent response of the HttpHeader
    final HttpHeader httpHeaderMock = mock(HttpHeader.class);
    when(httpHeaderMock.isExpectContent()).thenAnswer(invocation -> readResults.hasNext());

    return new BlockingTransferInputStream(httpHeaderMock, fccMock);
  }

  private HttpContent httpContentFromString(String content, boolean last) {
    final HttpHeader httpHeaderMock = mock(HttpHeader.class);
    return HttpContent.builder(httpHeaderMock).last(last).content(Buffers.wrap(DEFAULT_MEMORY_MANAGER, content)).build();
  }

  private ReadResult<HttpContent, ?> readResultFromString(String content, boolean last) {
    final HttpContent httpContent = httpContentFromString(content, last);

    // ReadResult can't be mocked because most of its methods are final, so we just create a dummy instance
    // We don't care about the connection and address parameters, only the content to be returned
    return ReadResult.create(null, httpContent, null, 0);
  }
}
