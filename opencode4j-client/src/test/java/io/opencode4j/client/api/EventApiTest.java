package io.opencode4j.client.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.opencode4j.OpenCodeException;
import io.opencode4j.client.HttpHeaders;
import io.opencode4j.client.SseStream;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.Test;

class EventApiTest extends MockServerTest {

    private static final String FRAME = "id: 1\nevent: message\ndata: {\"type\":\"server.connected\"}\n\n";

    @Test
    void opensTheV2EventStreamAndReadsLines() throws Exception {
        // Given
        server.enqueue(sse("text/event-stream", FRAME));

        // When
        String firstData;
        try (SseStream stream = client.events().open()) {
            String line;
            do {
                line = stream.readLine();
            } while (line != null && !line.startsWith("data:"));
            firstData = line;
        }

        // Then
        assertEquals("data: {\"type\":\"server.connected\"}", firstData);
        RecordedRequest request = takeRequest();
        assertEquals("GET", request.getMethod());
        assertEquals("/api/event", request.getPath());
        assertEquals(HttpHeaders.TEXT_EVENT_STREAM, request.getHeader(HttpHeaders.ACCEPT));
    }

    @Test
    void acceptsContentTypeParameters() throws Exception {
        // Given
        server.enqueue(sse("text/event-stream; charset=utf-8", FRAME));

        // When
        try (SseStream stream = client.events().open()) {
            // Then
            assertEquals("id: 1", stream.readLine());
        }
    }

    @Test
    void failsFastWhenTheServerAnswersWithHtml() {
        // Given: an older or newer server whose route table does not serve /api/event
        server.enqueue(sse("text/html", "<!doctype html><html></html>"));

        // When
        OpenCodeException failure = assertThrows(OpenCodeException.class, () -> client.events().open());

        // Then
        assertTrue(failure.getMessage().contains("text/event-stream"), failure.getMessage());
        assertTrue(failure.getMessage().contains("text/html"), failure.getMessage());
        assertEquals(200, failure.statusCode());
    }

    @Test
    void failsFastWhenTheContentTypeIsMissing() {
        // Given
        server.enqueue(new MockResponse().setBody(FRAME));

        // Then
        assertThrows(OpenCodeException.class, () -> client.events().open());
    }

    @Test
    void reportsAuthenticationFailures() {
        // Given
        server.enqueue(new MockResponse().setResponseCode(401).setBody("{\"_tag\":\"UnauthorizedError\"}"));

        // When
        OpenCodeException failure = assertThrows(OpenCodeException.class, () -> client.events().open());

        // Then
        assertEquals(401, failure.statusCode());
        assertNull(failure.getCause());
    }

    private MockResponse sse(String contentType, String body) {
        return new MockResponse().addHeader(HttpHeaders.CONTENT_TYPE, contentType).setBody(body);
    }
}
