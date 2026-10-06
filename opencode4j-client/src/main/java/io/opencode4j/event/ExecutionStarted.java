package io.opencode4j.event;

/** The server started working through the session inbox. */
public record ExecutionStarted(
        String sessionId
) implements OpenCodeEvent {

    @Override
    public String type() {
        return "session.execution.started";
    }
}
