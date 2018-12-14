/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.service.http.impl.service.client;

import org.mule.runtime.api.streaming.Cursor;

import com.ning.http.client.generators.InputStreamBodyGenerator;

import java.io.IOException;

import java.io.InputStream;

public class InputCursorBodyGenerator extends InputStreamBodyGenerator {

  private Cursor inputCursor;

  public InputCursorBodyGenerator(InputStream inputStream) {
    super(inputStream);
    inputCursor = (Cursor) inputStream;
  }

  @Override
  protected ISBody doCreateBody() {
    return new CursorBasedBody();
  }

  protected class CursorBasedBody extends ISBody {

    @Override
    protected int doRead() throws IOException {
      if (inputCursor.isReleased()) {
        return -1;
      } else {
        return super.doRead();
      }
    }
  }
}

