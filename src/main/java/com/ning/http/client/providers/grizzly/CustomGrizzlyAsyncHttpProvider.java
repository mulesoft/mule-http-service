package com.ning.http.client.providers.grizzly;

import com.ning.http.client.AsyncHandler;
import com.ning.http.client.AsyncHttpClientConfig;
import com.ning.http.client.BodyGenerator;
import com.ning.http.client.ConnectionPoolPartitioning;
import com.ning.http.client.FluentCaseInsensitiveStringsMap;
import com.ning.http.client.ListenableFuture;
import com.ning.http.client.NameResolver;
import com.ning.http.client.Param;
import com.ning.http.client.ProxyServer;
import com.ning.http.client.Realm;
import com.ning.http.client.Request;
import com.ning.http.client.cookie.Cookie;
import com.ning.http.client.multipart.Part;
import com.ning.http.client.uri.Uri;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.apache.logging.log4j.ThreadContext;

/**
 * Implementation of a {@link GrizzlyAsyncHttpProvider} that propagates the
 * contents of the {@link ThreadContext} as an attribute of the connection.
 */
public class CustomGrizzlyAsyncHttpProvider extends GrizzlyAsyncHttpProvider {

  public CustomGrizzlyAsyncHttpProvider(AsyncHttpClientConfig clientConfig) {
    super(clientConfig);
  }

  @Override
  public <T> ListenableFuture<T> execute(Request request, AsyncHandler<T> asyncHandler) {
    return super.execute(enrichRequestWithLogContext(request), asyncHandler);
  }

  @Override
  void execute(HttpTransactionContext transactionCtx) throws IOException {
    if (transactionCtx.getAhcRequest() instanceof LogContextEnrichedRequest) {
      LogContextEnrichedRequest lcer = (LogContextEnrichedRequest) transactionCtx.getAhcRequest();
      transactionCtx.getConnection().getAttributes().setAttribute("logContext", lcer.getLogContext());
    }
    super.execute(transactionCtx);
  }

  public Request enrichRequestWithLogContext(Request request) {
    return new LogContextEnrichedRequest(request, ThreadContext.getContext());
  }

  private class LogContextEnrichedRequest implements Request {

    final private Request request;
    final private Map<String, String> logContext;

    LogContextEnrichedRequest(Request request, Map<String, String> logContext) {
      this.request = request;
      this.logContext = logContext;
    }

    public Map<String, String> getLogContext() {
      return logContext;
    }

    @Override
    public String getMethod() {
      return request.getMethod();
    }

    @Override
    public Uri getUri() {
      return request.getUri();
    }

    @Override
    public String getUrl() {
      return request.getUrl();
    }

    @Override
    public InetAddress getInetAddress() {
      return request.getInetAddress();
    }

    @Override
    public InetAddress getLocalAddress() {
      return request.getLocalAddress();
    }

    @Override
    public FluentCaseInsensitiveStringsMap getHeaders() {
      return request.getHeaders();
    }

    @Override
    public Collection<Cookie> getCookies() {
      return request.getCookies();
    }

    @Override
    public byte[] getByteData() {
      return request.getByteData();
    }

    @Override
    public List<byte[]> getCompositeByteData() {
      return request.getCompositeByteData();
    }

    @Override
    public String getStringData() {
      return request.getStringData();
    }

    @Override
    public InputStream getStreamData() {
      return request.getStreamData();
    }

    @Override
    public BodyGenerator getBodyGenerator() {
      return request.getBodyGenerator();
    }

    @Override
    public long getContentLength() {
      return request.getContentLength();
    }

    @Override
    public List<Param> getFormParams() {
      return request.getFormParams();
    }

    @Override
    public List<Part> getParts() {
      return request.getParts();
    }

    @Override
    public String getVirtualHost() {
      return request.getVirtualHost();
    }

    @Override
    public List<Param> getQueryParams() {
      return request.getQueryParams();
    }

    @Override
    public ProxyServer getProxyServer() {
      return request.getProxyServer();
    }

    @Override
    public Realm getRealm() {
      return request.getRealm();
    }

    @Override
    public File getFile() {
      return request.getFile();
    }

    @Override
    public Boolean getFollowRedirect() {
      return request.getFollowRedirect();
    }

    @Override
    public int getRequestTimeout() {
      return request.getRequestTimeout();
    }

    @Override
    public long getRangeOffset() {
      return request.getRangeOffset();
    }

    @Override
    public String getBodyEncoding() {
      return request.getBodyEncoding();
    }

    @Override
    public ConnectionPoolPartitioning getConnectionPoolPartitioning() {
      return request.getConnectionPoolPartitioning();
    }

    @Override
    public NameResolver getNameResolver() {
      return request.getNameResolver();
    }
  }
}
