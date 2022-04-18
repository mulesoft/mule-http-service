/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.service.http.impl.service;

public class TraceData {

  private final long receivedBytesAmount;
  private final long sentBytesAmount;

  public TraceData(long receivedBytesAmount, long sentBytesAmount) {
    this.receivedBytesAmount = receivedBytesAmount;
    this.sentBytesAmount = sentBytesAmount;
  }

  public long getReceivedBytesAmount() {
    return receivedBytesAmount;
  }

  public long getSentBytesAmount() {
    return sentBytesAmount;
  }
}
