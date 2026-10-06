package io.opencode4j.client.api;

import io.opencode4j.client.HttpMethod;
import io.opencode4j.client.OpenCodeTransport;
import io.opencode4j.model.FormReplyRequest;
import io.opencode4j.model.PermissionDecision;
import io.opencode4j.model.PermissionReplyRequest;
import io.opencode4j.model.form.FormValue;
import java.util.Map;
import java.util.Objects;

/** Answers the questions an agent asks while it works: permission requests and forms. */
public final class InteractionApi {

    private static final String SESSION_PATH = "/api/session/";

    private final OpenCodeTransport transport;

    public InteractionApi(OpenCodeTransport transport) {
        this.transport = transport;
    }

    /**
     * Answers a permission request.
     *
     * @param message an optional note passed back to the agent, or {@code null}
     */
    public void replyPermission(String sessionId, String requestId, PermissionDecision decision, String message) {
        transport.send(
                HttpMethod.POST,
                path(sessionId, "/permission/", requestId) + "/reply",
                new PermissionReplyRequest(decision, message),
                Void.class);
    }

    /** Answers a form; {@code answer} maps each field key to its value. */
    public void replyForm(String sessionId, String formId, Map<String, FormValue> answer) {
        transport.send(
                HttpMethod.POST,
                path(sessionId, "/form/", formId) + "/reply",
                new FormReplyRequest(answer),
                Void.class);
    }

    /** Dismisses a form without answering it. */
    public void cancelForm(String sessionId, String formId) {
        transport.send(HttpMethod.DELETE, path(sessionId, "/form/", formId), null, Void.class);
    }

    /** Convenience for {@link #replyPermission} without a note. */
    public void replyPermission(String sessionId, String requestId, PermissionDecision decision) {
        replyPermission(sessionId, requestId, decision, null);
    }

    private String path(String sessionId, String segment, String id) {
        Objects.requireNonNull(sessionId, "sessionId");
        Objects.requireNonNull(id, "id");
        return SESSION_PATH + transport.segment(sessionId) + segment + transport.segment(id);
    }
}
