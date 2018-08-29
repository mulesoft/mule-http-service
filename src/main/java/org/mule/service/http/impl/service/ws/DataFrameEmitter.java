/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.service.http.impl.service.ws;

import java.util.concurrent.CompletableFuture;

public interface DataFrameEmitter {

  CompletableFuture<Void> stream(byte[] bytes, int offset, int len, boolean last);

  CompletableFuture<Void> send(byte[] bytes, int offset, int len);
}
