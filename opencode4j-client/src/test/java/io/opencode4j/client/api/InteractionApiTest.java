package io.opencode4j.client.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.opencode4j.client.HttpMethod;
import io.opencode4j.model.PermissionDecision;
import io.opencode4j.model.form.FormValue;
import java.util.Map;
import org.junit.jupiter.api.Test;

class InteractionApiTest extends MockServerTest {

    @Test
    void repliesToAPermissionRequest() throws Exception {
        // Given
        server.enqueue(noContent());
        server.enqueue(noContent());

        // When
        client.interactions().replyPermission("ses_1", "per_1", PermissionDecision.ONCE);
        client.interactions().replyPermission("ses_1", "per_1", PermissionDecision.REJECT, "not now");

        // Then
        assertRequest(HttpMethod.POST, "/api/session/ses_1/permission/per_1/reply", "{\"decision\":\"once\"}");
        assertRequest(
                HttpMethod.POST,
                "/api/session/ses_1/permission/per_1/reply",
                "{\"decision\":\"reject\",\"message\":\"not now\"}");
    }

    @Test
    void answersAForm() throws Exception {
        // Given
        server.enqueue(noContent());

        // When
        client.interactions().replyForm("ses_1", "frm_1", Map.of("q0", FormValue.of("A")));

        // Then
        assertRequest(HttpMethod.POST, "/api/session/ses_1/form/frm_1/reply", "{\"answer\":{\"q0\":\"A\"}}");
    }

    @Test
    void cancelsAForm() throws Exception {
        // Given
        server.enqueue(noContent());

        // When
        client.interactions().cancelForm("ses_1", "frm_1");

        // Then
        assertRequest(HttpMethod.DELETE, "/api/session/ses_1/form/frm_1", "");
    }

    @Test
    void rejectsMissingArgumentsBeforeSendingAnything() {
        // Then
        assertThrows(NullPointerException.class,
                () -> client.interactions().replyPermission("ses_1", null, PermissionDecision.ONCE));
        assertThrows(NullPointerException.class,
                () -> client.interactions().replyPermission("ses_1", "per_1", null));
        assertThrows(NullPointerException.class, () -> client.interactions().replyForm("ses_1", "frm_1", null));
        assertEquals(0, server.getRequestCount());
    }
}
