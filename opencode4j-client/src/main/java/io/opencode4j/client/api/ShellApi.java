package io.opencode4j.client.api;

import io.opencode4j.client.HttpMethod;
import io.opencode4j.client.OpenCodeTransport;
import java.util.Map;
import java.util.Objects;

/** Runs shell commands inside a session. */
public final class ShellApi {

    private final OpenCodeTransport transport;

    public ShellApi(OpenCodeTransport transport) {
        this.transport = transport;
    }

    /**
     * Runs a shell command as part of the session. Its output arrives on the event stream.
     */
    public void run(String sessionId, String command) {
        Objects.requireNonNull(sessionId, "sessionId");
        transport.send(
                HttpMethod.POST,
                "/api/session/" + transport.segment(sessionId) + "/shell",
                Map.of("command", Objects.requireNonNull(command, "command")),
                Void.class);
    }
}
