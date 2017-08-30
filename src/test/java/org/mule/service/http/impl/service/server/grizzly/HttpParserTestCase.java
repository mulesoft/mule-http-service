/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.service.http.impl.service.server.grizzly;

import static org.mule.service.http.impl.AllureConstants.HttpFeature.HTTP_SERVICE;
import static org.mule.service.http.impl.AllureConstants.HttpFeature.HttpStory.PARSING;
import static org.mule.service.http.impl.service.server.grizzly.HttpParser.normalizePathWithSpacesOrEncodedSpaces;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;

import org.mule.tck.junit4.AbstractMuleTestCase;
import org.mule.tck.size.SmallTest;

import org.junit.Test;
import io.qameta.allure.Feature;
import io.qameta.allure.Story;

@SmallTest
@Feature(HTTP_SERVICE)
@Story(PARSING)
public class HttpParserTestCase extends AbstractMuleTestCase {

  @Test
  public void normalizePath() {
    String expectedNormalizedPath = " some path";
    assertThat(normalizePathWithSpacesOrEncodedSpaces(expectedNormalizedPath), is(expectedNormalizedPath));
    assertThat(normalizePathWithSpacesOrEncodedSpaces("%20some%20path"), is(expectedNormalizedPath));
    assertThat(normalizePathWithSpacesOrEncodedSpaces("+some+path"), is(expectedNormalizedPath));
    assertThat(normalizePathWithSpacesOrEncodedSpaces("%20some+path"), is(expectedNormalizedPath));
    assertThat(normalizePathWithSpacesOrEncodedSpaces("+some%20path"), is(expectedNormalizedPath));
  }

}
