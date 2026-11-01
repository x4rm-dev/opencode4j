package io.opencode4j.client.api;

import com.fasterxml.jackson.core.type.TypeReference;
import io.opencode4j.client.HttpMethod;
import io.opencode4j.client.OpenCodeTransport;
import io.opencode4j.model.PermissionDecision;
import io.opencode4j.model.PermissionReplyRequest;
import io.opencode4j.model.PermissionRequest;
import java.util.List;
import java.util.Objects;

/**
 * Lists and answers permission requests.
 *
 * <p>A pending request blocks its session until answered. After a restart or a missed
 * {@code permission.asked} event, {@link #pending(String)} recovers what is still waiting. Note that a
 * {@link PermissionDecision#REJECT} answer interrupts the whole execution, not only the tool call.</p>
 */
public final class PermissionApi {

    private static final TypeReference<List<PermissionRequest>> REQUESTS = new TypeReference<>() { };

    private final OpenCodeTransport transport;

    public PermissionApi(OpenCodeTransport transport) {
        this.transport = transport;
    }

    /** The requests waiting for an answer in one session. */
    public List<PermissionRequest> pending(String sessionId) {
        List<PermissionRequest> requests = transport.send(HttpMethod.GET, sessionPath(sessionId) + "/permission",
                null, REQUESTS);
        return requests == null ? List.of() : requests;
    }

    /** The requests waiting for an answer across all sessions of a location. */
    public List<PermissionRequest> pendingInLocation(String directory) {
        List<PermissionRequest> requests = transport.send(HttpMethod.GET,
                transport.withLocation("/api/permission/request", directory), null, REQUESTS);
        return requests == null ? List.of() : requests;
    }

    /**
     * Answers a permission request.
     *
     * @param message an optional note passed back to the agent, or {@code null}
     */
    public void reply(String sessionId, String requestId, PermissionDecision decision, String message) {
        Objects.requireNonNull(requestId, "requestId");
        transport.send(
                HttpMethod.POST,
                sessionPath(sessionId) + "/permission/" + transport.segment(requestId) + "/reply",
                new PermissionReplyRequest(decision, message),
                Void.class);
    }

    private String sessionPath(String sessionId) {
        return "/api/session/" + transport.segment(Objects.requireNonNull(sessionId, "sessionId"));
    }
}
