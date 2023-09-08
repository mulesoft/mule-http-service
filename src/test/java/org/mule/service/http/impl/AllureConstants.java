/*
 * Copyright 2023 Salesforce, Inc. All rights reserved.
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.service.http.impl;

public interface AllureConstants {

  interface HttpFeature {

    String HTTP_SERVICE = "HTTP Service";

    interface HttpStory {

      String SERVER_MANAGEMENT = "Server Management";
      String RESPONSES = "Responses";
      String STREAMING = "Streaming";
      String PARSING = "Parsing";
      String MULTIPART = "Multipart";
      String LISTENERS = "Listeners";
      String TRANSFER_TYPE = "Transfer Type";
      String PROXIES = "Proxies";
      String CLIENT_AUTHENTICATION = "Client Authentication";
      String TLS = "TLS";
    }

  }
}
