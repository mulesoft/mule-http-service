/*
 * Copyright 2023 Salesforce, Inc. All rights reserved.
 */
package org.mule.service.http.impl.service.client;

import static java.lang.Long.parseLong;
import static org.mule.runtime.api.metadata.MediaType.MULTIPART_MIXED;
import static org.mule.runtime.http.api.HttpConstants.HttpStatus.NOT_MODIFIED;
import static org.mule.runtime.http.api.HttpConstants.HttpStatus.NO_CONTENT;
import static org.mule.runtime.http.api.HttpConstants.HttpStatus.RESET_CONTENT;
import static org.mule.runtime.http.api.HttpHeaders.Names.CONTENT_LENGTH;
import static org.mule.runtime.http.api.HttpHeaders.Names.CONTENT_TYPE;
import org.mule.runtime.http.api.domain.entity.EmptyHttpEntity;
import org.mule.runtime.http.api.domain.entity.HttpEntity;
import org.mule.runtime.http.api.domain.entity.InputStreamHttpEntity;
import org.mule.runtime.http.api.domain.message.response.HttpResponse;
import org.mule.runtime.http.api.domain.message.response.HttpResponseBuilder;
import org.mule.service.http.impl.service.domain.entity.multipart.StreamedMultipartHttpEntity;

import com.ning.http.client.Response;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map.Entry;

/**
 * Converts {@link Response Responses} to {@link HttpResponse HttpResponses}.
 *
 * @since 1.0
 */
public class HttpResponseCreator {

  private static final String HEADER_CONTENT_TYPE = CONTENT_TYPE.toLowerCase();
  private static final String HEADER_CONTENT_LENGTH = CONTENT_LENGTH.toLowerCase();

  public HttpResponse create(Response response, InputStream inputStream) throws IOException {
    HttpResponseBuilder responseBuilder = HttpResponse.builder();
    responseBuilder.statusCode(response.getStatusCode());
    responseBuilder.reasonPhrase(response.getStatusText());

    String contentType = null;
    String contentLength = null;
    if (response.hasResponseHeaders()) {
      for (Entry<String, List<String>> headerEntry : response.getHeaders().entrySet()) {
        String headerName = headerEntry.getKey();
        responseBuilder.addHeaders(headerName, headerEntry.getValue());

        if (headerName.equalsIgnoreCase(HEADER_CONTENT_TYPE)) {
          contentType = response.getHeader(HEADER_CONTENT_TYPE);
        } else if (headerName.equalsIgnoreCase(HEADER_CONTENT_LENGTH)) {
          contentLength = response.getHeader(HEADER_CONTENT_LENGTH);
        }
      }
    }

    responseBuilder.entity(createEntity(inputStream, contentType, contentLength, response.getStatusCode()));

    return responseBuilder.build();
  }

  private HttpEntity createEntity(InputStream stream, String contentType, String contentLength, int statusCode) {
    long contentLengthAsLong = -1L;
    if (contentLength != null) {
      contentLengthAsLong = parseLong(contentLength);
    }
    if (contentType != null && contentType.startsWith(MULTIPART_MIXED.getPrimaryType())) {
      if (contentLengthAsLong >= 0) {
        return new StreamedMultipartHttpEntity(stream, contentType, contentLengthAsLong);
      } else {
        return new StreamedMultipartHttpEntity(stream, contentType);
      }
    } else {
      if (contentLengthAsLong > 0) {
        return new InputStreamHttpEntity(stream, contentLengthAsLong);
      } else if (contentLengthAsLong == 0) {
        return new EmptyHttpEntity();
      } else if (statusCode == NO_CONTENT.getStatusCode() || statusCode == NOT_MODIFIED.getStatusCode()
          || statusCode == RESET_CONTENT.getStatusCode()) {
        return new EmptyHttpEntity();
      } else {
        return new InputStreamHttpEntity(stream);
      }
    }
  }

}
