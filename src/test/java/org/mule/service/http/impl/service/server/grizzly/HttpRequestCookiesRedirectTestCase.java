package org.mule.service.http.impl.service.server.grizzly;

import io.qameta.allure.Feature;
import io.qameta.allure.Story;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mule.runtime.http.api.client.HttpClient;
import org.mule.runtime.http.api.client.HttpClientConfiguration;
import org.mule.runtime.http.api.domain.message.request.HttpRequest;
import org.mule.runtime.http.api.domain.message.response.HttpResponse;
import org.mule.runtime.http.api.domain.message.response.HttpResponseBuilder;
import org.mule.service.http.impl.functional.client.AbstractHttpClientTestCase;

import static org.apache.http.HttpHeaders.LOCATION;
import static org.glassfish.grizzly.http.util.Header.Cookie;
import static org.glassfish.grizzly.http.util.Header.SetCookie;
import static org.hamcrest.core.IsNot.not;
import static org.hamcrest.core.StringContains.containsString;
import static org.hamcrest.text.IsEmptyString.isEmptyOrNullString;
import static org.junit.Assert.assertThat;
import static org.mule.runtime.http.api.HttpConstants.HttpStatus.MOVED_TEMPORARILY;
import static org.mule.runtime.http.api.HttpConstants.HttpStatus.OK;
import static org.mule.runtime.http.api.HttpConstants.Method.GET;
import static org.mule.service.http.impl.AllureConstants.HttpFeature.HTTP_SERVICE;
import static org.mule.service.http.impl.AllureConstants.HttpFeature.HttpStory.CLIENT_COOKIES;

@Feature(HTTP_SERVICE)
@Story(CLIENT_COOKIES)
public class HttpRequestCookiesRedirectTestCase extends AbstractHttpClientTestCase {

    private static final String COOKIE_EXP_DATE = "expires=Thu, 01-Jan-1970 00:00:01 GMT;";
    private static final String COOKIE_EXPIRED = "MyExpiredCookie=deleted;";
    private static final String COOKIE_VALID = "MyWorkingCookie=workingvalue;";

    private static final String REDIRECT_PATH = "/redirect";
    private static final String FINAL_PATH = "/final";

    private HttpClient client;
    private String cookiesSent;
    private HttpClientConfiguration.Builder clientBuilder = new HttpClientConfiguration.Builder().setName("cookies-test");

    public HttpRequestCookiesRedirectTestCase(String serviceToLoad) {
        super(serviceToLoad);
    }

    @Before
    public void setUpClient() {
        client = service.getClientFactory().create(clientBuilder.build());
        client.start();
        cookiesSent = "";
    }

    @After
    public void stopClient() {
        if (client != null) {
            client.stop();
        }
    }

    @Test
    public void onRedirectCookiesRemainSet() throws Exception {
        HttpRequest request = getRequest(REDIRECT_PATH);

        client.send(request);

        assertThat(cookiesSent, not(isEmptyOrNullString()));
        assertThat(cookiesSent, containsString(COOKIE_EXPIRED));
    }

    @Override
    protected HttpResponse setUpHttpResponse(HttpRequest request) {
        HttpResponseBuilder responseBuilder = HttpResponse.builder();

        if (request.getUri().getPath().equals(REDIRECT_PATH)) {
            responseBuilder.statusCode(MOVED_TEMPORARILY.getStatusCode())
                    .reasonPhrase(MOVED_TEMPORARILY.getReasonPhrase());
            responseBuilder.addHeader(LOCATION, getUri() + FINAL_PATH);
            setResponseCookies(responseBuilder);
        } else {
            cookiesSent = request.getHeaderValue(Cookie.toString());
            responseBuilder.statusCode(OK.getStatusCode())
                    .reasonPhrase(OK.getReasonPhrase());
        }
        return responseBuilder.build();
    }

    private void setResponseCookies(HttpResponseBuilder response) {
        response.addHeader(SetCookie.toString(), COOKIE_VALID);
        response.addHeader(SetCookie.toString(), COOKIE_EXPIRED + COOKIE_EXP_DATE);
    }

    private HttpRequest getRequest(String path) {
        return HttpRequest.builder().method(GET).uri(getUri() + path).build();
    }
}
