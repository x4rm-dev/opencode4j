package io.opencode4j.client.api;

import com.fasterxml.jackson.core.type.TypeReference;
import io.opencode4j.client.HttpMethod;
import io.opencode4j.client.OpenCodeTransport;
import io.opencode4j.model.Page;
import io.opencode4j.model.PageRequest;
import io.opencode4j.model.message.Message;
import java.util.List;
import java.util.Objects;

/**
 * Reads a session's message history.
 *
 * <p>The history is the authoritative record of a session: use it to reconcile after missed stream events,
 * to read a turn's final answer, and to rebuild transcripts.</p>
 */
public final class MessageApi {

    private static final TypeReference<List<Message>> MESSAGES = new TypeReference<>() { };

    private final OpenCodeTransport transport;

    public MessageApi(OpenCodeTransport transport) {
        this.transport = transport;
    }

    /**
     * Lists one page of a session's history, newest first unless the request says otherwise.
     *
     * @throws io.opencode4j.OpenCodeNotFoundException if the session does not exist
     * @throws io.opencode4j.OpenCodeValidationException if the cursor is invalid
     */
    public Page<Message> list(String sessionId, PageRequest page) {
        return Pages.get(transport, Pages.withPage(transport, messagesPath(sessionId), page), MESSAGES);
    }

    /** Reads one message. */
    public Message get(String sessionId, String messageId) {
        Objects.requireNonNull(messageId, "messageId");
        return transport.send(HttpMethod.GET, messagesPath(sessionId) + "/" + transport.segment(messageId), null,
                Message.class);
    }

    /**
     * Reads what the model currently sees for the session: the history after the latest completed
     * compaction, oldest first.
     */
    public List<Message> context(String sessionId) {
        List<Message> messages = transport.send(HttpMethod.GET, sessionPath(sessionId) + "/context", null, MESSAGES);
        return messages == null ? List.of() : messages;
    }

    private String messagesPath(String sessionId) {
        return sessionPath(sessionId) + "/message";
    }

    private String sessionPath(String sessionId) {
        return "/api/session/" + transport.segment(Objects.requireNonNull(sessionId, "sessionId"));
    }
}
