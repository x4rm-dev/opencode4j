package io.opencode4j.event;

/** A session has no work in progress. Deprecated by the server in favour of {@link SessionStatusChanged}. */
public record SessionIdle(
        String sessionId
) implements OpenCodeEvent {

    @Override
    public String type() {
        return "session.idle";
    }
}
