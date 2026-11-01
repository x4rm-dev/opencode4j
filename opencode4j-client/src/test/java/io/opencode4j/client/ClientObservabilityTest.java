package io.opencode4j.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.opencode4j.OpenCodeException;
import io.opencode4j.client.event.ConnectionState;
import io.opencode4j.client.event.EventFilter;
import io.opencode4j.client.event.StreamGap;
import io.opencode4j.config.OpenCodeConfig;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ClientObservabilityTest {

    private MockWebServer server;
    private final List<String> measured = new CopyOnWriteArrayList<>();
    private final ClientMetrics metrics = new ClientMetrics() {
        @Override
        public void httpRequest(String method, String route, int status, long durationNanos) {
            measured.add("http " + method + " " + route + " " + status);
        }

        @Override
        public void streamStateChanged(ConnectionState state) {
            measured.add("state " + state);
        }

        @Override
        public void streamGap(StreamGap.Reason reason) {
            measured.add("gap " + reason);
        }

        @Override
        public void unknownEvent(String type) {
            measured.add("unknown " + type);
        }
    };

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
    }

    @AfterEach
    void tearDown() throws IOException {
        server.shutdown();
    }

    private OpenCodeClient client(RequestHeaders headers) {
        return OpenCodeClient.builder(OpenCodeConfig.builder(server.url("/").toString()).servePassword("pw")
                        .sseReconnectBaseDelayMs(20).sseReconnectMaxDelayMs(40).build())
                .requestHeaders(headers).metrics(metrics).build();
    }

    @Test
    void addsRequestHeadersButNeverReplacesCredentials() throws Exception {
        // Given
        server.enqueue(new MockResponse().setHeader("Content-Type", "application/json")
                .setBody("{\"data\":{\"id\":\"ses_1\"}}"));
        try (OpenCodeClient client = client(() -> Map.of("traceparent", "00-abc-def-01",
                "Authorization", "Bearer stolen", "Cookie", "x=y"))) {

            // When
            client.sessions().get("ses_1");

            // Then
            RecordedRequest request = server.takeRequest(1, TimeUnit.SECONDS);
            assertEquals("00-abc-def-01", request.getHeader("traceparent"));
            assertTrue(request.getHeader("Authorization").startsWith("Basic "));
            assertEquals(null, request.getHeader("Cookie"));
        }
    }

    @Test
    void measuresRequestsByRouteWithoutIdsOrQueries() {
        // Given
        server.enqueue(new MockResponse().setHeader("Content-Type", "application/json").setBody("{\"data\":[]}"));
        server.enqueue(new MockResponse().setResponseCode(404));
        try (OpenCodeClient client = client(RequestHeaders.none())) {

            // When
            client.messages().list("ses_e6d020593ffe4q46YQ96agDiQ0", null);
            assertThrows(OpenCodeException.class, () -> client.sessions().get("ses_missing123"));

            // Then
            assertEquals(List.of("http GET /api/session/{id}/message 200", "http GET /api/session/{id} 404"), measured);
        }
    }

    @Test
    void templatesRoutes() {
        assertEquals("/api/session/{id}/permission/{id}/reply",
                HttpRoutes.template("/api/session/ses_e6d020593ffe/permission/per_192fdfc74001/reply"));
        assertEquals("/api/agent", HttpRoutes.template("/api/agent?location%5Bdirectory%5D=%2Fw"));
        assertEquals("/api/fs/read/{path}", HttpRoutes.template("/api/fs/read/src/main/App.java"));
        assertEquals("/api/session/active", HttpRoutes.template("/api/session/active"));
    }

    @Test
    void reportsStreamStateGapsAndUnknownEvents() throws Exception {
        // Given: one connection with an unmodelled event that then drops, and a second that stays open
        String first = "data: {\"type\":\"server.connected\",\"data\":{}}\n\n"
                + "data: {\"type\":\"session.brand.new\",\"data\":{}}\n\n";
        server.enqueue(new MockResponse().setHeader("Content-Type", "text/event-stream").setBody(first));
        String second = "data: {\"type\":\"server.connected\",\"data\":{}}\n\n";
        server.enqueue(new MockResponse().setHeader("Content-Type", "text/event-stream").setBody(second)
                .setHeader("Content-Length", second.length() + 10_000));
        try (OpenCodeClient client = client(RequestHeaders.none())) {

            // When
            client.eventBus().subscribe(EventFilter.all(), event -> { });
            await(() -> measured.contains("gap RECONNECT") && measured.stream()
                    .filter("state LIVE"::equals).count() == 2);

            // Then
            assertTrue(measured.contains("unknown session.brand.new"), measured.toString());
            assertTrue(measured.indexOf("state CONNECTING") < measured.indexOf("state LIVE"));
            assertTrue(measured.contains("state RECONNECTING"));
            assertFalse(measured.contains("state FAILED"));
        }
    }

    @Test
    void checksTheTestedServerRange() {
        assertTrue(ServerCompatibility.supports("2.2.0"));
        assertTrue(ServerCompatibility.supports("v2.2.7-beta.1"));
        assertFalse(ServerCompatibility.supports("2.1.4"));
        assertFalse(ServerCompatibility.supports("2.3.0"));
        assertFalse(ServerCompatibility.supports("dev"));
    }

    private static void await(BooleanSupplier condition) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (!condition.getAsBoolean()) {
            if (System.nanoTime() > deadline) {
                throw new AssertionError("condition not met");
            }
            Thread.sleep(10);
        }
    }
}
