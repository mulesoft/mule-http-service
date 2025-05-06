/*
 * Copyright 2023 Salesforce, Inc. All rights reserved.
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.service.http.impl.service.client.sse;

import static java.lang.Long.parseLong;

import static org.slf4j.LoggerFactory.getLogger;

import org.mule.runtime.http.api.sse.ServerSentEvent;
import org.mule.runtime.http.api.sse.client.SseListener;
import org.mule.service.http.impl.service.message.sse.SseEventBuilder;

import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import org.slf4j.Logger;

/**
 * Implementation of {@link ProgressiveBodyDataListener} that parses instances of {@link ServerSentEvent} and calls a
 * {@link SseListener} for each parsed instance.
 */
public class ServerSentEventDecoder implements ProgressiveBodyDataListener {

  private static final Logger LOGGER = getLogger(ServerSentEventDecoder.class);

  // Java8 regex equivalent to "\\r?\\n|\\r" (essentially matches any combination of \r and \n).
  private static final String LINE_BREAK_REGEX = "\\R";

  private final SseEventBuilder eventBuilder = new SseEventBuilder();
  private final SseListener eventListener;

  private String notParsedData = "";
  private CompletableFuture<InputStream> inputStream = new CompletableFuture<>();

  public ServerSentEventDecoder(SseListener eventListener) {
    this.eventListener = eventListener;
  }

  @Override
  public void onStreamCreated(InputStream inputStream) {
    if (this.inputStream.isDone()) {
      throw new IllegalStateException("Another input stream was already added to the event decoder");
    }
    LOGGER.trace("Event stream created");
    this.inputStream.complete(inputStream);
  }

  /**
   * This method is called when a number of bytes is available in the stream. It must not be called concurrently.
   * 
   * @param newDataLength the newly available number of bytes.
   */
  @Override
  public void onDataAvailable(int newDataLength) {
    InputStream in = null;
    try {
      in = this.inputStream.get();
    } catch (InterruptedException | ExecutionException e) {
      throw new RuntimeException(e);
    }

    LOGGER.trace("{} bytes available in the event stream. Reading...", newDataLength);
    byte[] data = new byte[newDataLength];
    try {
      int actualRead = in.read(data);
      if (actualRead != newDataLength) {
        LOGGER.warn("Expected to read {} bytes from the event stream, but actually read {}", newDataLength, actualRead);
      }
    } catch (IOException e) {
      throw new IllegalStateException("Exception parsing an events stream", e);
    }

    // TODO (W-18041071): Optimize this parsing to avoid data duplication.
    notParsedData += new String(data);
    String[] lines = notParsedData.split(LINE_BREAK_REGEX, -1);
    int linesNumber = lines.length;

    for (int i = 0; i < linesNumber - 1; i++) {
      LOGGER.trace("Handling line: {}", lines[i]);
      handleLine(lines[i]);
    }

    // the last element is the string after the last line separator
    notParsedData = lines[linesNumber - 1];
    LOGGER.trace("Found data after last line separator: {}", notParsedData);
  }

  private void handleLine(String line) {
    if (null == line || line.isEmpty()) {
      ServerSentEvent serverSentEvent = eventBuilder.buildAndClear();
      LOGGER.debug("Reading server-sent event: {}", serverSentEvent);
      this.eventListener.onEvent(serverSentEvent);
    } else if (line.startsWith("data:")) {
      eventBuilder.withData(line.substring(5).trim());
    } else if (line.startsWith("event:")) {
      eventBuilder.withName(line.substring(6).trim());
    } else if (line.startsWith("id:")) {
      eventBuilder.withId(line.substring(3).trim());
    } else if (line.startsWith("retry:")) {
      eventBuilder.withRetryDelay(parseRetryDelay(line.substring(6).trim()));
    }
  }

  // Spec states that if the retry delay is not a number, we should just ignore it.
  private Long parseRetryDelay(String asString) {
    try {
      return parseLong(asString);
    } catch (NumberFormatException e) {
      LOGGER.debug("Failed to parse retry delay because '{}' is not a number", asString, e);
      return null;
    }
  }

  @Override
  public void onEndOfStream() {
    LOGGER.trace("End of stream");
    eventListener.onClose();
  }
}
