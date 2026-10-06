package io.opencode4j.event;

/** A session was created; {@code parentId} is set for sub-agent child sessions. */
public record SessionCreated(
        String sessionId,
        String parentId,
        String title
) implements OpenCodeEvent {

    @Override
    public String type() {
        return "session.created";
    }
}
