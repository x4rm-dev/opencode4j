package io.opencode4j.client.api;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.opencode4j.model.PermissionDecision;
import io.opencode4j.model.PermissionRequest;
import java.util.List;
import org.junit.jupiter.api.Test;

class PermissionApiTest extends MockServerTest {

    // Captured from OpenCode 2.2.0 while a shell call waited for permission.
    private static final String PENDING = "{\"data\":[{\"id\":\"per_1\",\"sessionID\":\"ses_1\",\"action\":\"shell\","
            + "\"resources\":[\"echo hi\"],\"save\":[\"echo *\"],\"source\":{\"type\":\"tool\","
            + "\"messageID\":\"msg_1\",\"id\":\"call_1\"}}]}";

    @Test
    void listsPendingRequestsOfASession() throws Exception {
        // Given
        server.enqueue(json(PENDING));

        // When
        List<PermissionRequest> pending = client.permissions().pending("ses_1");

        // Then
        assertEquals("/api/session/ses_1/permission", takeRequest().getPath());
        PermissionRequest request = pending.getFirst();
        assertEquals("shell", request.action());
        assertEquals(List.of("echo hi"), request.resources());
        assertEquals(List.of("echo *"), request.save());
        assertEquals("call_1", request.source().callId());
        assertEquals("msg_1", request.source().messageId());
    }

    @Test
    void listsPendingRequestsOfALocation() throws Exception {
        // Given
        server.enqueue(json("{\"location\":{\"directory\":\"/w\"},\"data\":[]}"));

        // When
        List<PermissionRequest> pending = client.permissions().pendingInLocation("/w");

        // Then
        assertEquals(List.of(), pending);
        assertEquals("/api/permission/request?location%5Bdirectory%5D=%2Fw", takeRequest().getPath());
    }

    @Test
    void repliesWithDecisionAndNote() throws Exception {
        // Given
        server.enqueue(noContent());

        // When
        client.permissions().reply("ses_1", "per_1", PermissionDecision.REJECT, "read-only agent");

        // Then
        assertRequest("POST", "/api/session/ses_1/permission/per_1/reply",
                "{\"decision\":\"reject\",\"message\":\"read-only agent\"}");
    }
}
