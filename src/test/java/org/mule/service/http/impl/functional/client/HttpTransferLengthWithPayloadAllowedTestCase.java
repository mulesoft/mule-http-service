/*
 * Copyright 2023 Salesforce, Inc. All rights reserved.
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.service.http.impl.functional.client;

import static org.mule.service.http.impl.AllureConstants.HttpFeature.HttpStory.TRANSFER_TYPE;

import org.mule.service.http.impl.functional.client.AbstractHttpTransferLengthTestCase;

import io.qameta.allure.Story;

@Story(TRANSFER_TYPE)
public class HttpTransferLengthWithPayloadAllowedTestCase extends AbstractHttpTransferLengthTestCase {

  public HttpTransferLengthWithPayloadAllowedTestCase(String serviceToLoad) {
    super(serviceToLoad, true);
  }
}
