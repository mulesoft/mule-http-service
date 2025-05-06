/*
 * Copyright 2023 Salesforce, Inc. All rights reserved.
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.service.http.impl.service.message.sse;

import org.mule.runtime.http.api.sse.ServerSentEvent;

import java.io.IOException;
import java.io.Writer;
import java.util.Optional;

/**
 * Encodes a {@link ServerSentEvent} and writes it to a {@link Writer}.
 * <p>
 * The format of an encoded event is:
 * 
 * <pre>
 * {@code
 * event: event-name\n
 * data: data-line-1\n
 * data: data-line-2\n
 * data: data-line-3\n
 * [id: event-id\n]
 * [retry: retry-delay-millis\n]
 * \n}
 * </pre>
 */
public class SseEntityEncoder {

  // Java8 regex equivalent to "\\r?\\n|\\r" (essentially matches any combination of \r and \n).
  private static final String LINE_BREAK_REGEX = "\\R";

  /**
   * Encodes an event and writes the serialized representation to a writer.
   * 
   * @param writer destination writer.
   * @param event  event to be written.
   * @throws IOException if some error happens while trying to write to the writer.
   */
  public void writeTo(Writer writer, ServerSentEvent event) throws IOException {
    writer.write("event: ");
    writer.write(event.getName());
    writer.write("\n");

    for (String dataLine : event.getData().split(LINE_BREAK_REGEX)) {
      writer.write("data: ");
      writer.write(dataLine);
      writer.write("\n");
    }

    Optional<String> optionalId = event.getId();
    if (optionalId.isPresent()) {
      String id = optionalId.get();
      writer.write("id: ");
      writer.write(id);
      writer.write("\n");
    }

    Optional<Long> optionalNewRetryDelay = event.getRetryDelay();
    if (optionalNewRetryDelay.isPresent()) {
      long newDelay = optionalNewRetryDelay.get();
      writer.write("retry: " + newDelay);
      writer.write("\n");
    }

    writer.write("\n");
    writer.flush();
  }
}
