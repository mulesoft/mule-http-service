/*
 * Copyright 2023 Salesforce, Inc. All rights reserved.
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.service.http.impl.service.domain.entity.multipart;

import static java.util.Optional.ofNullable;
import org.mule.runtime.core.api.util.IOUtils;
import org.mule.runtime.http.api.domain.entity.HttpEntity;
import org.mule.runtime.http.api.domain.entity.multipart.HttpPart;
import org.mule.service.http.impl.service.server.grizzly.HttpParser;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.Optional;

/**
 * Represents a received multipart body, which can be accessed raw or parsed.
 *
 * @since 1.0
 */
public class StreamedMultipartHttpEntity implements HttpEntity {

  private InputStream content;
  private String contentType;
  private Long contentLength;

  public StreamedMultipartHttpEntity(InputStream content, String contentType) {
    this.content = content;
    this.contentType = contentType;
  }

  public StreamedMultipartHttpEntity(InputStream content, String contentType, Long contentLength) {
    this(content, contentType);
    this.contentLength = contentLength;
  }

  @Override
  public boolean isStreaming() {
    return true;
  }

  @Override
  public boolean isComposed() {
    return true;
  }

  @Override
  public InputStream getContent() throws UnsupportedOperationException {
    return content;
  }

  @Override
  public byte[] getBytes() throws UnsupportedOperationException {
    return IOUtils.toByteArray(content);
  }

  @Override
  public Collection<HttpPart> getParts() throws IOException, UnsupportedOperationException {
    return HttpParser.parseMultipartContent(content, contentType);
  }

  @Override
  public Optional<Long> getLength() {
    return ofNullable(contentLength);
  }

}
