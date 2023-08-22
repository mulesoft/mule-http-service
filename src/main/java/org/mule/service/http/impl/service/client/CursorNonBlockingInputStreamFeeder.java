/*
 * Copyright 2023 Salesforce, Inc. All rights reserved.
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.service.http.impl.service.client;

import java.io.IOException;

import org.mule.runtime.api.streaming.bytes.CursorStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ning.http.client.providers.grizzly.FeedableBodyGenerator;
import com.ning.http.client.providers.grizzly.NonBlockingInputStreamFeeder;

/**
 * Inputstream feeder used so that we guarantee that a cursor inputstream is properly reset.
 * 
 * @since 1.5.0
 *
 */
public class CursorNonBlockingInputStreamFeeder extends NonBlockingInputStreamFeeder {

  private static final Logger LOGGER = LoggerFactory.getLogger(CursorNonBlockingInputStreamFeeder.class);

  public CursorNonBlockingInputStreamFeeder(FeedableBodyGenerator feedableBodyGenerator, CursorStream content,
                                            int internalBufferSize) {
    super(feedableBodyGenerator, content, internalBufferSize);
  }

  @Override
  public void reset() {
    if (!content.markSupported()) {
      try {
        ((CursorStream) content).seek(0);
      } catch (IOException e) {
        LOGGER.warn("Unable to perform seek(0) on cursor stream", e);
      }
    }

    super.reset();
  }

}
