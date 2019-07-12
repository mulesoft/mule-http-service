/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.service.http.impl;

import static org.apache.http.client.config.AuthSchemes.NTLM;
import static org.mule.runtime.http.api.HttpConstants.HttpStatus.OK;
import static org.mule.runtime.http.api.HttpConstants.HttpStatus.UNAUTHORIZED;
import static org.mule.runtime.http.api.HttpHeaders.Names.AUTHORIZATION;
import static org.mule.runtime.http.api.HttpHeaders.Names.WWW_AUTHENTICATE;
import static org.slf4j.LoggerFactory.getLogger;
import org.mule.runtime.http.api.domain.message.request.HttpRequest;
import org.mule.runtime.http.api.domain.message.response.HttpResponse;
import org.mule.runtime.http.api.domain.message.response.HttpResponseBuilder;

import org.slf4j.Logger;

/**
 * Test NTLM authentication response generator
 */
public class NtlmMockResponseGenerator {

  private final Logger LOGGER = getLogger(NtlmMockResponseGenerator.class);

  public enum State {
    NOT_INITIATED, AUTH_REQUESTED, CHALLENGE_SENT, SUCCESS, FAILURE
  }

  private final String firstMessageExpectedHeader;
  private final String challenge;
  private final String secondMessageExpectedHeader;

  private State currentState;

  public NtlmMockResponseGenerator(String firstMessageExpectedHeader, String challenge,
                                   String secondMessageExpectedHeader) {
    this.firstMessageExpectedHeader = firstMessageExpectedHeader;
    this.challenge = challenge;
    this.secondMessageExpectedHeader = secondMessageExpectedHeader;
    this.currentState = State.NOT_INITIATED;
  }

  /**
   * Generates a response, following the NTLM Authentication dance-steps, verifying the provided credentials.
   *
   * @param request the request for whom to generate a response
   * @return a response to the request passed as argument
   */
  public HttpResponseBuilder generateForRequest(HttpRequest request) {
    HttpResponseBuilder responseBuilder = HttpResponse.builder();
    String authorization = request.getHeaderValue(AUTHORIZATION);
    // Client does not know the target service is requires authentication
    if (authorization == null) {
      responseBuilder.statusCode(UNAUTHORIZED.getStatusCode());
      responseBuilder.addHeader(WWW_AUTHENTICATE, NTLM);
      // Client knows that NTLM Authentication is required
    } else if (authorization.equals(this.firstMessageExpectedHeader)) {
      responseBuilder.statusCode(UNAUTHORIZED.getStatusCode());
      responseBuilder.addHeader(WWW_AUTHENTICATE, this.challenge);
      this.currentState = State.CHALLENGE_SENT;
      // First message with challenge sent, verifying outcome of dace
    } else if (authorization
        .equals(this.secondMessageExpectedHeader)) {
      responseBuilder.statusCode(OK.getStatusCode());
      this.currentState = State.SUCCESS;
    } else {
      responseBuilder.statusCode(UNAUTHORIZED.getStatusCode());
      LOGGER.error("NTLM dance failed in state: {}", this.currentState.toString());
      LOGGER.error("Recieved request: {}", request.getHeaderValue(AUTHORIZATION));
      this.currentState = State.FAILURE;
    }

    return responseBuilder;
  }

  /**
   * Gets the current state the NTLM dance is in.
   *
   * @return the state the dance is in
   */
  public State getState() {
    return currentState;
  }
}


