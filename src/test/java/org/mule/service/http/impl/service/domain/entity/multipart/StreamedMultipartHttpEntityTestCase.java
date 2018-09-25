/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.service.http.impl.service.domain.entity.multipart;

import static org.hamcrest.Matchers.arrayWithSize;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertThat;
import static org.mule.runtime.api.metadata.MediaType.APPLICATION_JSON;
import static org.mule.runtime.api.metadata.MediaType.TEXT;
import static org.mule.service.http.impl.AllureConstants.HttpFeature.HTTP_SERVICE;

import org.mule.runtime.core.api.util.IOUtils;
import org.mule.runtime.http.api.domain.entity.HttpEntity;
import org.mule.runtime.http.api.domain.entity.multipart.HttpPart;
import org.mule.tck.junit4.AbstractMuleTestCase;

import org.junit.Before;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;

import io.qameta.allure.Feature;
import io.qameta.allure.Story;

@Feature(HTTP_SERVICE)
@Story("Entities")
public class StreamedMultipartHttpEntityTestCase extends AbstractMuleTestCase {

  private static final String MULTIPART_CONTENT =
      "--the-boundary\r\n"
          + "Content-Disposition: form-data; name=\"img\"; filename=\"a.png\"\r\n"
          + "Content-Type: application/json\r\n"
          + "\r\n"
          + "{\r\n"
          + "\t\"key\" : \"value\"\r\n"
          + "}\r\n"
          + "--the-boundary\r\n"
          + "Content-Disposition: form-data; name=\"foo\"\r\n"
          + "\r\n"
          + "bar\r\n"
          + "--the-boundary\r\n";

  private HttpEntity entity;

  @Before
  public void setUp() throws IOException {
    entity = new StreamedMultipartHttpEntity(new ByteArrayInputStream(MULTIPART_CONTENT.getBytes()),
                                             "multipart/form-data; boundary=the-boundary");
  }

  @Test
  public void composed() {
    assertThat(entity.isComposed(), is(true));
  }

  @Test
  public void streaming() {
    assertThat(entity.isStreaming(), is(true));
  }

  @Test
  public void providesArrayIfUnparsed() throws IOException {
    assertThat(entity.getBytes(), is(equalTo(MULTIPART_CONTENT.getBytes())));
  }

  @Test
  public void failsToProvideArrayIfParsed() throws IOException {
    assertThat(entity.getParts(), hasSize(2));
    assertThat(entity.getBytes().length, is(0));
  }

  @Test
  public void providesStreamIfUnparsed() throws IOException {
    assertThat(IOUtils.toString(entity.getContent()), is(MULTIPART_CONTENT));
  }

  @Test
  public void failsToProvideStreamIfParsed() throws IOException {
    assertThat(entity.getParts(), hasSize(2));
    assertThat(IOUtils.toString(entity.getContent()), is(""));
  }

  @Test
  public void hasParts() throws IOException {
    Object[] parts = entity.getParts().toArray();
    assertThat(parts, arrayWithSize(2));

    HttpPart part1 = (HttpPart) parts[0];
    assertThat(part1.getContentType(), is(APPLICATION_JSON.toRfcString()));
    assertThat(part1.getName(), is("img"));
    assertThat(part1.getFileName(), is("a.png"));
    assertThat(IOUtils.toString(part1.getInputStream()), is("{\r\n"
        + "\t\"key\" : \"value\"\r\n"
        + "}"));

    HttpPart part2 = (HttpPart) parts[1];
    assertThat(part2.getContentType(), is(TEXT.toRfcString()));
    assertThat(part2.getName(), is("foo"));
    assertThat(part2.getFileName(), is(nullValue()));
    assertThat(IOUtils.toString(part2.getInputStream()), is("bar"));
  }

  @Test
  public void hasNoSizeUnlessSpecified() {
    assertThat(entity.getBytesLength().isPresent(), is(false));
    HttpEntity specifiedEntity = new StreamedMultipartHttpEntity(new ByteArrayInputStream(MULTIPART_CONTENT.getBytes()),
                                                                 "multipart/form-data; boundary=the-boundary",
                                                                 (long) MULTIPART_CONTENT.length());
    assertThat(specifiedEntity.getBytesLength().getAsLong(), is((long) MULTIPART_CONTENT.length()));
  }

}
