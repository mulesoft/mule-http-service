/*
 * Copyright 2023 Salesforce, Inc. All rights reserved.
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.service.http.impl.service.client.sse;

import java.io.InputStream;

/**
 * No-op implementation of {@link ProgressiveBodyDataListener}.
 */
public class NoOpProgressiveBodyDataListener implements ProgressiveBodyDataListener {

  @Override
  public void onStreamCreated(InputStream inputStream) {}

  @Override
  public void onDataAvailable(int newDataLength) {}

  @Override
  public void onEndOfStream() {}
}
