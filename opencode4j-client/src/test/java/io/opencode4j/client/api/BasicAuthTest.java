package io.opencode4j.client.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import io.opencode4j.client.HttpHeaders;
import io.opencode4j.client.OpenCodeClient;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.Test;

class BasicAuthTest extends MockServerTest {

    @Test
    void usesTheDefaultUsernameWhenOnlyAPasswordIsConfigured() throws Exception {
        // Given
        client = OpenCodeClient.create(config().servePassword("pw").build());
        server.enqueue(json("{\"version\":\"2.0.20\"}"));

        // When
        client.server().isHealthy();

        // Then
        assertEquals(basic("opencode:pw"), takeRequest().getHeader(HttpHeaders.AUTHORIZATION));
    }

    @Test
    void usesTheConfiguredUsername() throws Exception {
        // Given
        client = OpenCodeClient.create(config().serveUsername("admin").servePassword("pw").build());
        server.enqueue(json("{\"version\":\"2.0.20\"}"));

        // When
        client.server().isHealthy();

        // Then
        assertEquals(basic("admin:pw"), takeRequest().getHeader(HttpHeaders.AUTHORIZATION));
    }

    @Test
    void sendsNoAuthorizationHeaderWithoutAPassword() throws Exception {
        // Given
        server.enqueue(json("{\"version\":\"2.0.20\"}"));

        // When
        client.server().isHealthy();

        // Then
        RecordedRequest request = takeRequest();
        assertNull(request.getHeader(HttpHeaders.AUTHORIZATION));
    }

    private static String basic(String credentials) {
        return "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
    }
}
