/*
 * Copyright 2023 Salesforce, Inc. All rights reserved.
 */
package org.mule.service.http.impl.service.server.grizzly;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mule.runtime.http.api.HttpHeaders.Names.CONTENT_ID;
import static org.mule.service.http.impl.service.server.grizzly.HttpMultipartEncoder.AMBIGUOUS_TYPE_ERROR_MESSAGE;
import static org.mule.service.http.impl.service.server.grizzly.HttpMultipartEncoder.MANDATORY_TYPE_ERROR_MESSAGE;
import static org.mule.service.http.impl.service.server.grizzly.HttpMultipartEncoder.toMimeMultipart;

import java.util.ArrayList;
import java.util.List;

import javax.mail.BodyPart;
import javax.mail.internet.MimeMultipart;

import org.junit.Before;
import org.junit.Test;

import org.mule.runtime.http.api.domain.entity.HttpEntity;
import org.mule.runtime.http.api.domain.entity.multipart.HttpPart;
import org.mule.tck.junit4.AbstractMuleTestCase;

public class HttpMultipartEncoderTestCase extends AbstractMuleTestCase {

  private static final String FIRST_PART_CONTENT = "first-part-test";

  private static final String SECOND_PART_CONTENT = "second-part-test";

  private static final String THIRD_PART_CONTENT = "third-part-test";

  private static final String FORTH_PART_CONTENT = "{ \"test\" : \"forth-part-test\"}";

  private final List<HttpPart> httpParts = new ArrayList<>();

  private final HttpEntity httpEntity = mock(HttpEntity.class);

  @Before
  public void setUp() throws Exception {
    HttpPart firstPart =
        new HttpPart("firstPart", FIRST_PART_CONTENT.getBytes(), "text/plain", FIRST_PART_CONTENT.getBytes().length);
    httpParts.add(firstPart);

    HttpPart secondPart =
        new HttpPart("secondPart", SECOND_PART_CONTENT.getBytes(), "text/plain", SECOND_PART_CONTENT.getBytes().length);
    httpParts.add(secondPart);

    HttpPart thirdPart =
        new HttpPart("thirdPart", THIRD_PART_CONTENT.getBytes(), "text/plain", THIRD_PART_CONTENT.getBytes().length);
    httpParts.add(thirdPart);

    when(httpEntity.getParts()).thenReturn(httpParts);
  }

  @Test
  public void createMultipartRelatedContentWithStartParameter() throws Exception {
    MimeMultipart mimeMultipart =
        toMimeMultipart(httpEntity, "multipart/related; boundary= \"MIMEBoundary\"; type=\"text/plain\"; start=thirdPart");
    verifyBodyPart(mimeMultipart.getBodyPart(0), THIRD_PART_CONTENT, "thirdPart");
    verifyBodyPart(mimeMultipart.getBodyPart(1), FIRST_PART_CONTENT, "firstPart");
    verifyBodyPart(mimeMultipart.getBodyPart(2), SECOND_PART_CONTENT, "secondPart");
    assertThat(mimeMultipart.getContentType(), containsString("type=\"text/plain\""));
  }

  @Test
  public void createMultipartRelatedContentWithoutStartParameter() throws Exception {
    MimeMultipart mimeMultipart =
        toMimeMultipart(httpEntity, "multipart/related; boundary=\"MIMEBoundary\"; type=\"text/plain\";");
    verifyBodyPart(mimeMultipart.getBodyPart(0), FIRST_PART_CONTENT, "firstPart");
    verifyBodyPart(mimeMultipart.getBodyPart(1), SECOND_PART_CONTENT, "secondPart");
    verifyBodyPart(mimeMultipart.getBodyPart(2), THIRD_PART_CONTENT, "thirdPart");
    assertThat(mimeMultipart.getContentType(), containsString("type=\"text/plain\""));
  }

  @Test
  public void createMultipartRelatedContentWithoutMandatoryTypeParameter() throws Exception {
    try {
      toMimeMultipart(httpEntity, "multipart/related; boundary=\"MIMEBoundary\"");
      fail("Exception caused by no present type should be triggered");
    } catch (Exception e) {
      assertThat(e.getMessage(), is(MANDATORY_TYPE_ERROR_MESSAGE));
    }
  }

  @Test
  public void createMultipartRelatedContentWithAmbiguousType() throws Exception {
    HttpPart forthPart =
        new HttpPart("forthPart", FORTH_PART_CONTENT.getBytes(), "application/json", FORTH_PART_CONTENT.getBytes().length);
    httpParts.add(forthPart);
    try {
      toMimeMultipart(httpEntity, "multipart/related; boundary=\"MIMEBoundary\"; type=\"text/plain\"; start=forthPart");
      fail("Exception caused by ambiguous type should be triggered");
    } catch (Exception e) {
      assertThat(e.getMessage(), is(AMBIGUOUS_TYPE_ERROR_MESSAGE));
    }
  }

  private void verifyBodyPart(BodyPart bodyPart, String content, String name) throws Exception {
    assertThat(bodyPart.getContent(), is(content));
    assertThat(bodyPart.getHeader(CONTENT_ID)[0], is(name));
  }

}
