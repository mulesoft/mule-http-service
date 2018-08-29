/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.service.http.impl.service.ws;

import static java.lang.String.format;
import static org.mule.runtime.api.i18n.I18nMessageFactory.createStaticMessage;
import org.mule.runtime.api.exception.MuleRuntimeException;
import org.mule.runtime.http.api.ws.FragmentHandler;

import java.io.IOException;
import java.io.InputStream;
import java.io.PipedOutputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class PipedFragmentHandler implements FragmentHandler {

  private static final Logger LOGGER = LoggerFactory.getLogger(PipedFragmentHandler.class);

  private final String socketId;
  private final PipedOutputStream pipe;
  private final ManagedPipedInputStream stream;
  private final Runnable onClose;
  private boolean pipeClosed = false;

  public PipedFragmentHandler(String socketId, Runnable onClose) {
    try {
      stream = new ManagedPipedInputStream();
      pipe = new PipedOutputStream(stream);
      this.socketId = socketId;
      this.onClose = onClose;
    } catch (IOException e) {
      throw new MuleRuntimeException(createStaticMessage("Couldn't connect pipe to stream"), e);
    }
  }

  @Override
  public boolean write(byte[] data) throws IOException {
    if (!stream.isOpen()) {
      complete();
      return false;
    }

    if (pipeClosed) {
      return false;
    }

    pipe.write(data);
    return true;
  }

  @Override
  public void complete() {
    if (pipeClosed) {
      return;
    }

    try {
      pipe.close();
    } catch (IOException e) {
      LOGGER.error(format("Could not properly close streaming pipe for socket '%s'. %s", socketId, e.getMessage()), e);
    } finally {
      pipeClosed = true;
      onClose.run();
    }
  }

  @Override
  public void abort() {
    complete();

    try {
      stream.close();
    } catch (IOException e) {
      LOGGER.error(format("Could not properly close stream for socket '%s'. %s", socketId, e.getMessage()), e);
    } finally {
      onClose.run();
    }
  }

  @Override
  public InputStream getInputStream() {
    return stream;
  }
}
