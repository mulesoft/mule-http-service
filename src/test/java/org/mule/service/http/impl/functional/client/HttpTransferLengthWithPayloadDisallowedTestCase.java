/*
 * Copyright 2023 Salesforce, Inc. All rights reserved.
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
