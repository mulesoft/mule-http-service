/*
 * Copyright 2023 Salesforce, Inc. All rights reserved.
 */
package org.mule.service.http.impl.service.client;

import java.io.IOException;

import org.mule.runtime.api.streaming.bytes.CursorStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ning.http.client.Body;
import com.ning.http.client.generators.InputStreamBodyGenerator;

/**
 * Input Stream Body Generator which properly resets input streams
 * 
 * @since 1.5.0
 *
 */
public class CursorInputStreamBodyGenerator extends InputStreamBodyGenerator {

  private static final Logger LOGGER = LoggerFactory.getLogger(CursorNonBlockingInputStreamFeeder.class);

  public CursorInputStreamBodyGenerator(CursorStream inputStream) {
    super(inputStream);
  }

  @Override
  public Body createBody() throws IOException {
    if (!inputStream.markSupported()) {
      try {
        ((CursorStream) inputStream).seek(0);
      } catch (IOException e) {
        LOGGER.warn("Unable to perform seek(0) on input stream", e);
      }
    }
    return super.createBody();
  }

}
