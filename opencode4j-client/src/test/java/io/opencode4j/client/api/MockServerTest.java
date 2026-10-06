package io.opencode4j.client.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import io.opencode4j.client.HttpHeaders;
import io.opencode4j.client.OpenCodeClient;
import io.opencode4j.config.OpenCodeConfig;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

/** Starts a {@link MockWebServer} and a client pointed at it for each test. */
abstract class MockServerTest {

    protected MockWebServer server;
    protected OpenCodeClient client;

    @BeforeEach
    void startServer() throws IOException {
        server = new MockWebServer();
        server.start();
        client = OpenCodeClient.create(config().build());
    }

    @AfterEach
    void stopServer() throws IOException {
        server.shutdown();
    }

    protected OpenCodeConfig.Builder config() {
        return OpenCodeConfig.builder(server.url("/").toString());
    }

    protected MockResponse json(String body) {
        return new MockResponse()
                .addHeader(HttpHeaders.CONTENT_TYPE, HttpHeaders.APPLICATION_JSON)
                .setBody(body);
    }

    protected MockResponse noContent() {
        return new MockResponse().setResponseCode(204);
    }

    protected RecordedRequest takeRequest() throws InterruptedException {
        RecordedRequest request = server.takeRequest(1, TimeUnit.SECONDS);
        assertNotNull(request, "expected a request to reach the server");
        return request;
    }

    protected void assertRequest(String method, String path, String body) throws InterruptedException {
        RecordedRequest request = takeRequest();
        assertEquals(method, request.getMethod());
        assertEquals(path, request.getPath());
        assertEquals(body, request.getBody().readUtf8());
    }
}
