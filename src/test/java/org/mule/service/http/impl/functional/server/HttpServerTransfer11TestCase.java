/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.service.http.impl.functional.server;

import static org.apache.http.HttpVersion.HTTP_1_1;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.mule.runtime.core.api.util.StringUtils.EMPTY;
import static org.mule.runtime.http.api.HttpHeaders.Values.CHUNKED;

import org.apache.http.HttpVersion;
import org.junit.Test;

public class HttpServerTransfer11TestCase extends HttpServerTransferTestCase {

  @Override
  public HttpVersion getVersion() {
    return HTTP_1_1;
  }

  @Test
  public void defaultsToLengthWhenEmpty() throws Exception {
    verifyTransferHeaders(EMPTY, is(nullValue()), is("0"), EMPTY);
  }

  @Test
  public void defaultsToLengthWhenBytes() throws Exception {
    verifyTransferHeaders(BYTES, is(nullValue()), is(DATA_SIZE), DATA);
  }

  @Test
  public void defaultsToLengthWhenMultipart() throws Exception {
    verifyTransferHeaders(MULTIPART, is(nullValue()), is(MULTIPART_SIZE), MULTIPART_DATA);
  }

  @Test
  public void defaultsToChunkedWhenStream() throws Exception {
    verifyTransferHeaders(STREAM, is(CHUNKED), is(nullValue()), DATA);
  }

  @Test
  public void usesChunkedWhenEmptyAndHeader() throws Exception {
    headerToSend = CHUNKED_PAIR;
    verifyTransferHeaders(EMPTY, is(CHUNKED), is(nullValue()), EMPTY);
  }

  @Test
  public void usesChunkedWhenBytesAndHeader() throws Exception {
    headerToSend = CHUNKED_PAIR;
    verifyTransferHeaders(BYTES, is(CHUNKED), is(nullValue()), DATA);
  }

  @Test
  public void usesChunkedWhenMultipartAndHeader() throws Exception {
    headerToSend = CHUNKED_PAIR;
    verifyTransferHeaders(MULTIPART, is(CHUNKED), is(nullValue()), MULTIPART_DATA);
  }

  @Test
  public void usesChunkedWhenStreamAndHeader() throws Exception {
    headerToSend = CHUNKED_PAIR;
    verifyTransferHeaders(STREAM, is(CHUNKED), is(nullValue()), DATA);
  }

}
