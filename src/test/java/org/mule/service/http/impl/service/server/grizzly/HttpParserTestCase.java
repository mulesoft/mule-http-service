/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.service.http.impl.service.server.grizzly;

import static java.lang.String.format;
import static org.mule.runtime.api.metadata.MediaType.MULTIPART_RELATED;
import static org.mule.service.http.impl.AllureConstants.HttpFeature.HTTP_SERVICE;
import static org.mule.service.http.impl.AllureConstants.HttpFeature.HttpStory.PARSING;
import static org.mule.service.http.impl.service.server.grizzly.HttpParser.normalizePathWithSpacesOrEncodedSpaces;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;
import static org.mule.service.http.impl.service.server.grizzly.HttpParser.parseMultipartContent;

import org.mule.runtime.http.api.domain.entity.multipart.HttpPart;
import org.mule.tck.junit4.AbstractMuleTestCase;
import org.mule.tck.size.SmallTest;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;

import org.junit.Test;
import io.qameta.allure.Feature;
import io.qameta.allure.Story;

@SmallTest
@Feature(HTTP_SERVICE)
@Story(PARSING)
public class HttpParserTestCase extends AbstractMuleTestCase {

  private static final String CONTENT_ID = "someContentId";
  private static final String MULTIPART_RELATED_WITH_CONTENT_ID =
      format("--the-boundary\r\n"
          + "Content-Type: text/plain\r\n"
          + "Content-ID: %s\r\n"
          + "\r\n"
          + "content" + "\r\n"
          + "--the-boundary\r\n", CONTENT_ID);

  @Test
  public void normalizePath() {
    String expectedNormalizedPath = " some path";
    assertThat(normalizePathWithSpacesOrEncodedSpaces(expectedNormalizedPath), is(expectedNormalizedPath));
    assertThat(normalizePathWithSpacesOrEncodedSpaces("%20some%20path"), is(expectedNormalizedPath));
    assertThat(normalizePathWithSpacesOrEncodedSpaces("+some+path"), is(expectedNormalizedPath));
    assertThat(normalizePathWithSpacesOrEncodedSpaces("%20some+path"), is(expectedNormalizedPath));
    assertThat(normalizePathWithSpacesOrEncodedSpaces("+some%20path"), is(expectedNormalizedPath));
  }

  @Test
  public void partWithoutNameButWithPresentContentIdHeader() throws IOException {
    InputStream content = new ByteArrayInputStream(MULTIPART_RELATED_WITH_CONTENT_ID.getBytes());
    Collection<HttpPart> httpPartCollection = parseMultipartContent(content, MULTIPART_RELATED.toString());
    assertThat(httpPartCollection.size(), is(1));
    HttpPart httpPart = httpPartCollection.iterator().next();
    assertThat(httpPart.getName(), is(CONTENT_ID));
    assertThat(true, is(false));
  }

}
