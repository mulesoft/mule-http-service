/*
 * Copyright 2023 Salesforce, Inc. All rights reserved.
 */
package org.mule.service.http.impl.service.server.grizzly;

import org.mule.runtime.api.util.Preconditions;
import org.mule.runtime.http.api.domain.request.ClientConnection;

import java.net.InetSocketAddress;
import java.security.cert.Certificate;

import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSession;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Provides information about the client connection
 */
public class DefaultClientConnection implements ClientConnection {

  private static final Logger logger = LoggerFactory.getLogger(DefaultClientConnection.class);
  private final SSLSession sslSession;
  private final InetSocketAddress remoteHostAddress;

  public DefaultClientConnection(InetSocketAddress remoteHostAddress) {
    Preconditions.checkArgument(remoteHostAddress != null, "remoteHostAddress cannot be null.");
    this.remoteHostAddress = remoteHostAddress;
    this.sslSession = null;
  }

  /**
   * @param sslSession        the SSL session
   * @param remoteHostAddress
   */
  public DefaultClientConnection(SSLSession sslSession, InetSocketAddress remoteHostAddress) {
    Preconditions.checkArgument(sslSession != null, "sslSession cannot be null.");
    Preconditions.checkArgument(remoteHostAddress != null, "remoteHostAddress cannot be null.");
    this.sslSession = sslSession;
    this.remoteHostAddress = remoteHostAddress;
  }

  public InetSocketAddress getRemoteHostAddress() {
    return remoteHostAddress;
  }

  public Certificate getClientCertificate() {
    try {
      if (sslSession != null) {
        Certificate[] peerCertificates = sslSession.getPeerCertificates();
        if (peerCertificates.length > 0) {
          return peerCertificates[0];
        }
      }
    } catch (SSLPeerUnverifiedException e) {
      if (logger.isDebugEnabled()) {
        logger.debug("Failure getting peer certificates", e);
      }
    }
    return null;
  }

}
