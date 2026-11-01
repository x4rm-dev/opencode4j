package io.opencode4j.event;

/** A session was deleted. */
public record SessionDeleted(
        String sessionId
) implements OpenCodeEvent {

    @Override
    public String type() {
        return "session.deleted";
    }
}
