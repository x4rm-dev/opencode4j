package io.opencode4j.agent.runtime.workspace;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.opencode4j.OpenCodeException;
import io.opencode4j.client.OpenCodeClient;
import io.opencode4j.config.OpenCodeConfig;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Set;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocationManagerTest {

    private MockWebServer server;
    private LocationManager manager;

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
        manager = new LocationManager(OpenCodeClient.create(
                OpenCodeConfig.builder(server.url("/").toString()).build()));
    }

    @AfterEach
    void tearDown() throws IOException {
        server.shutdown();
    }

    @Test
    void canonicalizesExistingAndMissingDirectories(@TempDir Path directory) throws IOException {
        assertEquals(directory.toRealPath(), manager.canonicalize(directory));
        Path missing = directory.resolve("missing");
        assertEquals(missing.toAbsolutePath().normalize(), manager.canonicalize(missing));
    }

    @Test
    void waitsForAllExpectedAgentIds(@TempDir Path directory) throws InterruptedException {
        server.enqueue(catalog("{\"id\":\"boss\",\"name\":\"worker\"}"));
        server.enqueue(catalog("{\"id\":\"boss\"},{\"id\":\"worker\"},{\"id\":\"worker\"}"));

        manager.warmLocation(directory, Set.of("boss", "worker"), Duration.ofSeconds(3));

        assertEquals(2, server.getRequestCount());
    }

    @Test
    void retriesTransientApiFailures(@TempDir Path directory) throws InterruptedException {
        server.enqueue(new MockResponse().setResponseCode(500));
        server.enqueue(catalog("{\"id\":\"boss\"}"));

        manager.warmLocation(directory, Set.of("boss"), Duration.ofSeconds(3));

        assertEquals(2, server.getRequestCount());
    }

    @Test
    void failsImmediatelyForAuthenticationAndAuthorizationErrors(@TempDir Path directory) {
        for (int status : new int[] {401, 403}) {
            server.enqueue(new MockResponse().setResponseCode(status));
            IllegalStateException failure = assertThrows(IllegalStateException.class,
                    () -> manager.warmLocation(directory, Set.of("boss"), Duration.ofSeconds(3)));

            assertTrue(failure.getMessage().contains("HTTP " + status));
            assertEquals(status, assertInstanceOf(OpenCodeException.class, failure.getCause()).statusCode());
        }
        assertEquals(2, server.getRequestCount());
    }

    @Test
    void reportsExpectedAgentsOnTimeout(@TempDir Path directory) {
        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> manager.warmLocation(directory, Set.of("boss"), Duration.ZERO));

        assertTrue(failure.getMessage().contains("boss"));
        assertTrue(failure.getMessage().contains("Visible agents were: []"));
        assertEquals(0, server.getRequestCount());
    }

    private MockResponse catalog(String agents) {
        return new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("{\"data\":[" + agents + "]}");
    }
}
