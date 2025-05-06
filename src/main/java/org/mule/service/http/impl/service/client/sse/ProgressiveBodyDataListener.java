/*
 * Copyright 2023 Salesforce, Inc. All rights reserved.
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.service.http.impl.service.client.sse;

import org.mule.runtime.http.api.domain.entity.HttpEntity;

import java.io.InputStream;

/**
 * Implementation internal interface to avoid depending on the {@link InputStream} of an {@link HttpEntity} and allow
 * implementations to handle the data as soon as it is received.
 */
public interface ProgressiveBodyDataListener {

  /**
   * Method to be called when the stream that represents the HTTP body is created. It should not be called twice.
   * 
   * @param inputStream stream representing the HTTP response body.
   */
  void onStreamCreated(InputStream inputStream);

  /**
   * Callback to be called when a certain number of bytes is available in the body stream.
   * 
   * @param newDataLength the newly available number of bytes.
   */
  void onDataAvailable(int newDataLength);

  /**
   * To be called when the body was fully received.
   */
  void onEndOfStream();
}
