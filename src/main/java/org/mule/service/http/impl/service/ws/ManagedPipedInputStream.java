/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.service.http.impl.service.ws;

import java.io.IOException;
import java.io.PipedInputStream;

public class ManagedPipedInputStream extends PipedInputStream {

  private boolean open = true;

  @Override
  public void close() throws IOException {
    if (open) {
      open = false;
      super.close();
    }
  }

  public boolean isOpen() {
    return open;
  }
}
