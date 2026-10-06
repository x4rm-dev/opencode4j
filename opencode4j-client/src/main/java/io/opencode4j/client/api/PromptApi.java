package io.opencode4j.client.api;

import io.opencode4j.client.HttpMethod;
import io.opencode4j.client.OpenCodeTransport;
import io.opencode4j.model.PromptReceipt;
import io.opencode4j.model.PromptRequest;
import java.util.Objects;

/** Sends prompts to sessions. */
public final class PromptApi {

    private final OpenCodeTransport transport;

    public PromptApi(OpenCodeTransport transport) {
        this.transport = transport;
    }

    /**
     * Adds a prompt to a session and, unless {@code resume} is {@code false}, starts working on it.
     *
     * <p>This returns as soon as the server has accepted the prompt. The agent's output arrives on the event
     * stream ({@code EventApi.open()}); open the stream first, because events are not replayed.</p>
     */
    public PromptReceipt send(String sessionId, PromptRequest request) {
        Objects.requireNonNull(sessionId, "sessionId");
        return transport.send(
                HttpMethod.POST,
                "/api/session/" + transport.segment(sessionId) + "/prompt",
                Objects.requireNonNull(request, "request"),
                PromptReceipt.class);
    }
}
