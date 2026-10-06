package io.opencode4j.client.api;

import com.fasterxml.jackson.core.type.TypeReference;
import io.opencode4j.client.HttpMethod;
import io.opencode4j.client.OpenCodeTransport;
import io.opencode4j.model.CreateSessionRequest;
import io.opencode4j.model.ModelRef;
import io.opencode4j.model.Session;
import io.opencode4j.model.SessionRevert;
import io.opencode4j.model.SnapshotFileDiff;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Creates, inspects and controls sessions. */
public final class SessionApi {

    private static final String SESSION_PATH = "/api/session";

    private final OpenCodeTransport transport;

    public SessionApi(OpenCodeTransport transport) {
        this.transport = transport;
    }

    /** Creates a session. The agent and model given here apply to every prompt sent to it. */
    public Session create(CreateSessionRequest request) {
        return transport.send(
                HttpMethod.POST, SESSION_PATH, Objects.requireNonNull(request, "request"), Session.class);
    }

    public Session get(String sessionId) {
        return transport.send(HttpMethod.GET, sessionPath(sessionId), null, Session.class);
    }

    /**
     * Lists the newest sessions (the server's first page, 50 by default).
     *
     * @param directory only sessions in this directory, or {@code null} for all
     */
    public List<Session> list(String directory) {
        return transport.send(
                HttpMethod.GET,
                transport.withDirectory(SESSION_PATH, directory),
                null,
                new TypeReference<List<Session>>() { });
    }

    /** Lists the sessions spawned by {@code parentSessionId}, for example by sub-agents. */
    public List<Session> children(String parentSessionId) {
        Objects.requireNonNull(parentSessionId, "parentSessionId");
        return transport.send(
                HttpMethod.GET,
                SESSION_PATH + "?parentID=" + transport.enc(parentSessionId),
                null,
                new TypeReference<List<Session>>() { });
    }

    /** Deletes a session and all its data. */
    public void delete(String sessionId) {
        delete(sessionId, null);
    }

    /** Deletes a session and all its data within a specific workspace directory. */
    public void delete(String sessionId, String directory) {
        String path = transport.withLocation(sessionPath(sessionId), directory);
        transport.send(HttpMethod.DELETE, path, null, Void.class);
    }

    /** Stops the session's running turn. */
    public void interrupt(String sessionId) {
        transport.send(HttpMethod.POST, sessionPath(sessionId) + "/interrupt", null, Void.class);
    }

    /** Switches the agent used for the session's next turns. */
    public void switchAgent(String sessionId, String agent) {
        transport.send(
                HttpMethod.POST,
                sessionPath(sessionId) + "/agent",
                Map.of("agent", Objects.requireNonNull(agent, "agent")),
                Void.class);
    }

    /** Switches the model used for the session's next turns. */
    public void switchModel(String sessionId, ModelRef model) {
        transport.send(
                HttpMethod.POST,
                sessionPath(sessionId) + "/model",
                Map.of("model", Objects.requireNonNull(model, "model")),
                Void.class);
    }

    /**
     * Forks the session into a new one.
     *
     * @param beforeMessageId fork the history before this message, or {@code null} to fork everything
     */
    public Session fork(String sessionId, String beforeMessageId) {
        Map<String, Object> body = beforeMessageId == null ? Map.of() : Map.of("before", beforeMessageId);
        return transport.send(HttpMethod.POST, sessionPath(sessionId) + "/fork", body, Session.class);
    }

    /**
     * Returns the file changes made by one turn or a range of turns.
     *
     * @param fromMessageId the user message whose turn to diff, or {@code null} for the whole session
     * @param toMessageId   a later user message whose turn ends the range, or {@code null}
     */
    public List<SnapshotFileDiff> diff(String sessionId, String fromMessageId, String toMessageId) {
        StringBuilder path = new StringBuilder(sessionPath(sessionId)).append("/diff");
        String separator = "?";
        if (fromMessageId != null) {
            path.append(separator).append("from=").append(transport.enc(fromMessageId));
            separator = "&";
        }
        if (toMessageId != null) {
            path.append(separator).append("to=").append(transport.enc(toMessageId));
        }
        return transport.send(HttpMethod.GET, path.toString(), null, new TypeReference<List<SnapshotFileDiff>>() { });
    }

    /** Stages a revert of the session back to before {@code messageId}; undo it with {@link #revertClear}. */
    public SessionRevert revertStage(String sessionId, String messageId) {
        return transport.send(
                HttpMethod.POST,
                sessionPath(sessionId) + "/revert/stage",
                Map.of("messageID", Objects.requireNonNull(messageId, "messageId")),
                SessionRevert.class);
    }

    /** Makes a staged revert permanent. */
    public void revertCommit(String sessionId) {
        transport.send(HttpMethod.POST, sessionPath(sessionId) + "/revert/commit", null, Void.class);
    }

    /** Discards a staged revert, restoring the session. */
    public void revertClear(String sessionId) {
        transport.send(HttpMethod.DELETE, sessionPath(sessionId) + "/revert", null, Void.class);
    }

    private String sessionPath(String sessionId) {
        Objects.requireNonNull(sessionId, "sessionId");
        return SESSION_PATH + "/" + transport.segment(sessionId);
    }
}
