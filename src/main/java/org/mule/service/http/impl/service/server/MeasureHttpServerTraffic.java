/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.service.http.impl.service.server;

import org.json.JSONObject;
import org.mule.runtime.http.api.server.HttpServer;
import org.mule.runtime.http.api.server.ServerNotFoundException;
import org.mule.runtime.module.troubleshooting.api.ArgumentDefinition;
import org.mule.runtime.module.troubleshooting.api.TroubleshootingOperation;
import org.mule.runtime.module.troubleshooting.api.TroubleshootingOperationCallback;
import org.mule.runtime.module.troubleshooting.api.TroubleshootingOperationDefinition;
import org.mule.runtime.module.troubleshooting.api.DefaultArgumentDefinition;
import org.mule.runtime.module.troubleshooting.api.DefaultTroubleshootingOperationDefinition;
import org.mule.service.http.impl.service.TraceData;
import org.mule.service.http.impl.service.server.grizzly.GrizzlyHttpServer;

public class MeasureHttpServerTraffic implements TroubleshootingOperation {

  private static final String MEASURE_HTTP_SERVER_TRAFFIC_OPERATION_NAME = "servertraffic";
  private static final String MEASURE_HTTP_SERVER_TRAFFIC_OPERATION_DESCRIPTION =
      "Retrieves the amount of bytes written and read by a given HTTP server";

  private static final String SERVER_NAME_ARGUMENT_NAME = "server";
  private static final String SERVER_NAME_ARGUMENT_DESCRIPTION = "The HTTP Server name (HTTP Listener Config name)";

  private static final String CONTEXT_NAME_ARGUMENT_NAME = "context";
  private static final String CONTEXT_NAME_ARGUMENT_DESCRIPTION = "The application or domain where the config is defined";

  private static final TroubleshootingOperationDefinition definition = createOperationDefinition();

  private final HttpServerManager httpServerManager;

  public MeasureHttpServerTraffic(HttpServerManager httpServerManager) {
    this.httpServerManager = httpServerManager;
  }

  @Override
  public TroubleshootingOperationDefinition getDefinition() {
    return definition;
  }

  @Override
  public TroubleshootingOperationCallback getCallback() {
    return arguments -> {
      try {
        JSONObject trafficData = new JSONObject();
        final String contextName = arguments.get(CONTEXT_NAME_ARGUMENT_NAME);
        final String serverName = arguments.get(SERVER_NAME_ARGUMENT_NAME);
        TraceData serverTraceData = httpServerManager.getServerTraceData(new ServerIdentifier(contextName, serverName));
        trafficData.put("received", serverTraceData.getReceivedBytesAmount());
        trafficData.put("sent", serverTraceData.getSentBytesAmount());
        return trafficData.toString(2);
      } catch (ServerNotFoundException e) {
        return "Server not found";
      }
    };
  }

  private static TroubleshootingOperationDefinition createOperationDefinition() {
    return new DefaultTroubleshootingOperationDefinition(MEASURE_HTTP_SERVER_TRAFFIC_OPERATION_NAME,
                                                         MEASURE_HTTP_SERVER_TRAFFIC_OPERATION_DESCRIPTION,
                                                         createServerNameArgumentDefinition(),
                                                         createContextNameArgumentDefinition());
  }

  private static ArgumentDefinition createServerNameArgumentDefinition() {
    return new DefaultArgumentDefinition(SERVER_NAME_ARGUMENT_NAME, SERVER_NAME_ARGUMENT_DESCRIPTION, true);
  }

  private static ArgumentDefinition createContextNameArgumentDefinition() {
    return new DefaultArgumentDefinition(CONTEXT_NAME_ARGUMENT_NAME, CONTEXT_NAME_ARGUMENT_DESCRIPTION, true);
  }
}
