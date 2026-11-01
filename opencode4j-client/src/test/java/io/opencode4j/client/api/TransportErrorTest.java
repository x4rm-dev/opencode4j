package io.opencode4j.client.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.opencode4j.OpenCodeAuthException;
import io.opencode4j.OpenCodeConflictException;
import io.opencode4j.OpenCodeException;
import io.opencode4j.OpenCodeNotFoundException;
import io.opencode4j.OpenCodeServerException;
import io.opencode4j.OpenCodeTransportException;
import io.opencode4j.OpenCodeValidationException;
import io.opencode4j.client.HttpHeaders;
import io.opencode4j.client.HttpMethod;
import io.opencode4j.model.Session;
import java.util.Map;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.SocketPolicy;
import org.junit.jupiter.api.Test;

class TransportErrorTest extends MockServerTest {

    private static final String SECRET_BODY = "{\"_tag\":\"SessionNotFound\",\"message\":\"prompt: top secret log line\"}";

    @Test
    void mapsEachStatusClassToItsExceptionType() {
        Map<Integer, Class<? extends OpenCodeException>> expected = Map.of(
                400, OpenCodeValidationException.class,
                401, OpenCodeAuthException.class,
                403, OpenCodeAuthException.class,
                404, OpenCodeNotFoundException.class,
                409, OpenCodeConflictException.class,
                422, OpenCodeValidationException.class,
                500, OpenCodeServerException.class,
                503, OpenCodeServerException.class);

        expected.forEach((status, type) -> {
            // Given
            server.enqueue(new MockResponse().setResponseCode(status).setBody("{\"_tag\":\"X\"}"));

            // When
            OpenCodeException failure = assertThrows(OpenCodeException.class,
                    () -> client.sessions().get("ses_1"));

            // Then
            assertInstanceOf(type, failure, "status " + status);
            assertEquals(status, failure.statusCode());
            assertEquals(status >= 500, failure.retryable(), "status " + status);
        });
    }

    @Test
    void rateLimitingIsRetryableButNotASubtype() {
        // Given
        server.enqueue(new MockResponse().setResponseCode(429));

        // When
        OpenCodeException failure = assertThrows(OpenCodeException.class, () -> client.sessions().get("ses_1"));

        // Then
        assertEquals(OpenCodeException.class, failure.getClass());
        assertTrue(failure.retryable());
    }

    @Test
    void keepsTheBodyOutOfTheMessageButExposesTagRequestIdAndBody() {
        // Given
        server.enqueue(new MockResponse().setResponseCode(404)
                .setHeader(HttpHeaders.REQUEST_ID, "req-9")
                .setBody(SECRET_BODY));

        // When
        OpenCodeNotFoundException failure = assertThrows(OpenCodeNotFoundException.class,
                () -> client.sessions().get("ses_1"));

        // Then
        assertFalse(failure.getMessage().contains("top secret"), failure.getMessage());
        assertTrue(failure.getMessage().contains("HTTP 404"), failure.getMessage());
        assertEquals("SessionNotFound", failure.errorCode());
        assertEquals("req-9", failure.requestId());
        assertEquals(SECRET_BODY, failure.responseBody());
    }

    @Test
    void readsTheErrorTagFromANestedErrorObject() {
        // Given
        server.enqueue(new MockResponse().setResponseCode(400)
                .setBody("{\"error\":{\"name\":\"ValidationError\",\"message\":\"bad\"}}"));

        // When
        OpenCodeValidationException failure = assertThrows(OpenCodeValidationException.class,
                () -> client.sessions().get("ses_1"));

        // Then
        assertEquals("ValidationError", failure.errorCode());
    }

    @Test
    void truncatesLargeBodies() {
        // Given
        server.enqueue(new MockResponse().setResponseCode(500).setBody("x".repeat(100_000)));

        // When
        OpenCodeServerException failure = assertThrows(OpenCodeServerException.class,
                () -> client.sessions().get("ses_1"));

        // Then
        assertEquals(2_048, failure.responseBody().length());
        assertNull(failure.errorCode());
    }

    @Test
    void connectionFailuresAreRetryableTransportExceptions() {
        // Given
        server.enqueue(new MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START));

        // When
        OpenCodeException failure = assertThrows(OpenCodeException.class, () -> client.sessions().get("ses_1"));

        // Then
        assertInstanceOf(OpenCodeTransportException.class, failure);
        assertTrue(failure.retryable());
        assertEquals(OpenCodeException.NO_STATUS, failure.statusCode());
    }

    @Test
    void undecodableBodiesAreNotRetryable() {
        // Given
        server.enqueue(json("not json"));

        // When
        OpenCodeException failure = assertThrows(OpenCodeException.class, () -> client.sessions().get("ses_1"));

        // Then
        assertEquals(OpenCodeException.class, failure.getClass());
        assertFalse(failure.retryable());
    }

    @Test
    void nullDataEnvelopeDecodesToNull() {
        // Given
        server.enqueue(json("{\"data\":null}"));
        server.enqueue(json("{\"data\":null,\"location\":{\"directory\":\"/w\"}}"));

        // Then
        assertNull(client.sessions().get("ses_1"));
        assertNull(client.sessions().get("ses_1"));
    }

    @Test
    void unwrapsEnvelopesAndAcceptsBareBodies() {
        // Given
        server.enqueue(json("{\"data\":{\"id\":\"ses_1\",\"title\":\"t\"}}"));
        server.enqueue(json("{\"id\":\"ses_2\",\"title\":\"bare\"}"));

        // When
        Session enveloped = client.sessions().get("ses_1");
        Session bare = client.sessions().get("ses_2");

        // Then
        assertEquals("ses_1", enveloped.id());
        assertEquals("bare", bare.title());
    }

    @Test
    void sendForTreeKeepsEnvelopeMetadata() {
        // Given
        server.enqueue(json("{\"data\":[{\"id\":\"a\"}],\"cursor\":{\"next\":\"n1\"}}"));

        // When
        var tree = client.transport().sendForTree(HttpMethod.GET, "/api/session", null);

        // Then
        assertEquals("n1", tree.path("cursor").path("next").asText());
        assertEquals("a", tree.path("data").get(0).path("id").asText());
    }
}
