package io.opencode4j.client.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.opencode4j.OpenCodeException;
import io.opencode4j.client.HttpMethod;
import io.opencode4j.model.ServerInfo;
import okhttp3.mockwebserver.MockResponse;
import org.junit.jupiter.api.Test;

class ServerApiTest extends MockServerTest {

    private static final String INFO = "{\"version\":\"2.0.20\",\"pid\":56782,"
            + "\"urls\":[\"http://127.0.0.1:4096\"],\"paths\":{\"tmp\":\"/tmp/opencode\"}}";

    @Test
    void readsServerInfoFromAnUnwrappedResponse() throws Exception {
        // Given
        server.enqueue(json(INFO));

        // When
        ServerInfo info = client.server().info();

        // Then
        assertEquals("2.0.20", info.version());
        assertEquals(56782, info.pid());
        assertRequest(HttpMethod.GET, "/api/info", "");
    }

    @Test
    void isHealthyWhenInfoAnswers() {
        // Given
        server.enqueue(json(INFO));

        // Then
        assertTrue(client.server().isHealthy());
    }

    @Test
    void isNotHealthyWhenAuthenticationIsRequiredButInfoExplainsWhy() {
        // Given
        MockResponse unauthorized = new MockResponse().setResponseCode(401)
                .setBody("{\"_tag\":\"UnauthorizedError\",\"message\":\"Authentication required\"}");
        server.enqueue(unauthorized);
        server.enqueue(unauthorized);

        // When
        boolean healthy = client.server().isHealthy();
        OpenCodeException failure = assertThrows(OpenCodeException.class, () -> client.server().info());

        // Then
        assertFalse(healthy);
        assertEquals(401, failure.statusCode());
    }
}
