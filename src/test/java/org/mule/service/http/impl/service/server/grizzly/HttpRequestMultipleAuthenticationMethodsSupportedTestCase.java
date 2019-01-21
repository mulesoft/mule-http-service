package org.mule.service.http.impl.service.server.grizzly;

import static org.junit.Assert.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mule.runtime.http.api.HttpConstants.HttpStatus.OK;
import static org.mule.service.http.impl.AllureConstants.HttpFeature.HTTP_SERVICE;
import static org.mule.service.http.impl.AllureConstants.HttpFeature.HttpStory.SERVER_MANAGEMENT;
import static java.util.concurrent.TimeUnit.SECONDS;

import org.glassfish.grizzly.http.HttpRequestPacket;
import org.mule.runtime.http.api.domain.message.response.HttpResponse;
import org.mule.runtime.http.api.server.HttpServer;
import org.mule.runtime.http.api.server.ServerAddress;
import org.mule.runtime.http.api.server.ServerCreationException;
import org.mule.runtime.http.api.server.async.ResponseStatusCallback;
import org.mule.service.http.impl.service.server.DefaultServerAddress;
import org.mule.service.http.impl.service.server.ServerIdentifier;

import io.qameta.allure.Feature;
import io.qameta.allure.Story;
import org.junit.Test;

import java.net.InetAddress;

@Feature(HTTP_SERVICE)
@Story(SERVER_MANAGEMENT)
public class HttpRequestMultipleAuthenticationMethodsSupportedTestCase extends AbstractGrizzlyServerManagerTestCase {

    @Override
    protected HttpServer getServer(ServerAddress address, ServerIdentifier id) throws ServerCreationException {
        HttpServer server = serverManager.createServerFor(address, () -> muleContext.getSchedulerService().ioScheduler(), true,
                (int) SECONDS.toMillis(DEFAULT_TEST_TIMEOUT_SECS), id);

        final ResponseStatusCallback responseStatusCallback = mock(ResponseStatusCallback.class);

        server.addRequestHandler("/*", ((requestContext, responseCallback) -> {
            responseCallback.responseReady(HttpResponse.builder().statusCode(OK.getStatusCode()).build(),
                    responseStatusCallback);
        }) );

        return server;
    }


    @Test
    public void httpRequestToMultipleAuthMethodServerSelectsCorrectAuthMethod() throws Exception {

        final HttpServer server = getServer(new DefaultServerAddress( InetAddress.getLocalHost(), listenerPort.getNumber()),
                new ServerIdentifier("context", "name"));

        server.start();

        HttpRequestPacket requestPacket = mock(HttpRequestPacket.class);

        assertFalse(true);

        //requestPacket.
    }
}
