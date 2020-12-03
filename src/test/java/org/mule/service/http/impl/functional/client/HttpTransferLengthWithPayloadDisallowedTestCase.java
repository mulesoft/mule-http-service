/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.service.http.impl.functional.client;

import static org.mule.service.http.impl.AllureConstants.HttpFeature.HttpStory.TRANSFER_TYPE;

import org.mule.service.http.impl.service.server.grizzly.AbstractHttpTransferLengthTestCase;

import io.qameta.allure.Story;

@Story(TRANSFER_TYPE)
public class HttpTransferLengthWithPayloadDisallowedTestCase extends AbstractHttpTransferLengthTestCase {

  public HttpTransferLengthWithPayloadDisallowedTestCase(String serviceToLoad) {
    super(serviceToLoad, false);
  }
}
