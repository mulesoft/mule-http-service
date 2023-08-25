/*
 * Copyright 2023 Salesforce, Inc. All rights reserved.
 */
package org.mule.service.http.impl.service.client;

import static com.ning.http.client.Realm.AuthScheme.NTLM;
import static com.ning.http.util.UTF8UrlEncoder.encodeQueryElement;
import static org.mule.runtime.core.api.util.IOUtils.toByteArray;

import com.ning.http.client.Realm;
import com.ning.http.client.Realm.RealmBuilder;
import com.ning.http.client.RequestBuilder;
import com.ning.http.client.generators.InputStreamBodyGenerator;
import com.ning.http.client.multipart.ByteArrayPart;
import com.ning.http.client.providers.grizzly.FeedableBodyGenerator;
import com.ning.http.client.providers.grizzly.NonBlockingInputStreamFeeder;
import org.mule.runtime.api.streaming.bytes.CursorStream;
import org.mule.runtime.core.api.util.func.CheckedConsumer;
import org.mule.runtime.http.api.client.HttpRequestOptions;
import org.mule.runtime.http.api.client.auth.HttpAuthentication;
import org.mule.runtime.http.api.client.auth.HttpAuthenticationType;
import org.mule.runtime.http.api.domain.entity.multipart.HttpPart;
import org.mule.runtime.http.api.domain.message.request.HttpRequest;
import org.mule.service.http.impl.service.client.GrizzlyHttpClient.RequestConfigurer;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map.Entry;

class GrizzlyRequestConfigurer implements RequestConfigurer {

  private final HttpRequestOptions options;
  private final HttpRequest request;
  private final GrizzlyHttpClient client;
  private final boolean enableMuleRedirect;
  private final boolean requestStreamingEnabled;
  private final int requestStreamingBufferSize;

  GrizzlyRequestConfigurer(GrizzlyHttpClient client, HttpRequestOptions options, HttpRequest request,
                           boolean enableMuleRedirect, boolean requestStreamingEnabled, int requestStreamingBufferSize) {
    this.client = client;
    this.options = options;
    this.request = request;
    this.enableMuleRedirect = enableMuleRedirect;
    this.requestStreamingEnabled = requestStreamingEnabled;
    this.requestStreamingBufferSize = requestStreamingBufferSize;
  }

  @Override
  public void configure(RequestBuilder builder) throws IOException {
    builder.setFollowRedirects(!enableMuleRedirect && options.isFollowsRedirect());

    client.populateHeaders(request, builder);

    for (Entry<String, String> entry : request.getQueryParams().entryList()) {
      builder.addQueryParam(entry.getKey() != null ? encodeQueryElement(entry.getKey()) : null,
                            entry.getValue() != null ? encodeQueryElement(entry.getValue()) : null);
    }
    options.getAuthentication().ifPresent((CheckedConsumer<HttpAuthentication>) (authentication -> {
      RealmBuilder realmBuilder = new RealmBuilder()
          .setPrincipal(authentication.getUsername())
          .setPassword(authentication.getPassword())
          .setUsePreemptiveAuth(authentication.isPreemptive());

      if (authentication.getType() == HttpAuthenticationType.BASIC) {
        realmBuilder.setScheme(Realm.AuthScheme.BASIC);
      } else if (authentication.getType() == HttpAuthenticationType.DIGEST) {
        realmBuilder.setScheme(Realm.AuthScheme.DIGEST);
      } else if (authentication.getType() == HttpAuthenticationType.NTLM) {
        String domain = ((HttpAuthentication.HttpNtlmAuthentication) authentication).getDomain();
        if (domain != null) {
          realmBuilder.setNtlmDomain(domain);
        }
        String workstation = ((HttpAuthentication.HttpNtlmAuthentication) authentication).getWorkstation();
        String ntlmHost = workstation != null ? workstation : client.getHostName();
        realmBuilder.setNtlmHost(ntlmHost).setScheme(NTLM);
      }

      builder.setRealm(realmBuilder.build());
    }));

    options.getProxyConfig().ifPresent(proxyConfig -> builder.setProxyServer(client.buildProxy(proxyConfig)));

    if (request.getEntity() != null) {
      if (request.getEntity().isStreaming()) {
        setStreamingBodyToRequestBuilder(request, builder);
      } else if (request.getEntity().isComposed()) {
        for (HttpPart part : request.getEntity().getParts()) {
          if (part.getFileName() != null) {
            builder.addBodyPart(new ByteArrayPart(part.getName(), toByteArray(part.getInputStream()),
                                                  part.getContentType(), null, part.getFileName()));
          } else {
            byte[] content = toByteArray(part.getInputStream());
            builder.addBodyPart(new ByteArrayPart(part.getName(), content, part.getContentType(), null));
          }
        }
      } else {
        builder.setBody(request.getEntity().getBytes());
      }
    }

    // Set the response timeout in the request, this value is read by {@code CustomTimeoutThrottleRequestFilter}
    // if the maxConnections attribute is configured in the requester.
    builder.setRequestTimeout(options.getResponseTimeout());
  }

  private void setStreamingBodyToRequestBuilder(HttpRequest request, RequestBuilder builder) throws IOException {
    if (requestStreamingEnabled) {
      FeedableBodyGenerator bodyGenerator = new FeedableBodyGenerator();
      bodyGenerator.setFeeder(new InputStreamFeederFactory(bodyGenerator, request.getEntity().getContent(),
                                                           requestStreamingBufferSize).getInputStreamFeeder());
      builder.setBody(bodyGenerator);
    } else {
      builder.setBody(new InputStreamBodyGeneratorFactory(request.getEntity().getContent()).getInputStreamBodyGenerator());
    }
  }

  private static class InputStreamFeederFactory {

    private FeedableBodyGenerator feedableBodyGenerator;
    private InputStream content;
    private int internalBufferSize;

    public InputStreamFeederFactory(FeedableBodyGenerator feedableBodyGenerator, InputStream content,
                                    int internalBufferSize) {

      this.feedableBodyGenerator = feedableBodyGenerator;
      this.content = content;
      this.internalBufferSize = internalBufferSize;
    }

    public NonBlockingInputStreamFeeder getInputStreamFeeder() {
      if (content instanceof CursorStream) {
        return new CursorNonBlockingInputStreamFeeder(feedableBodyGenerator, (CursorStream) content, internalBufferSize);
      }

      return new NonBlockingInputStreamFeeder(feedableBodyGenerator, content, internalBufferSize);
    }
  }

  private static class InputStreamBodyGeneratorFactory {

    private InputStream inputStream;

    public InputStreamBodyGeneratorFactory(InputStream inputStream) {
      this.inputStream = inputStream;
    }

    public InputStreamBodyGenerator getInputStreamBodyGenerator() {
      if (inputStream instanceof CursorStream) {
        return new CursorInputStreamBodyGenerator((CursorStream) inputStream);
      }

      return new InputStreamBodyGenerator(inputStream);
    }
  }
}
