/*
 * Copyright 2023 Salesforce, Inc. All rights reserved.
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
