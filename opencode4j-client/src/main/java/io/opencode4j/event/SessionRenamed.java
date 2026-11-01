package io.opencode4j.event;

/** A session's title changed. */
public record SessionRenamed(
        String sessionId,
        String title
) implements OpenCodeEvent {

    @Override
    public String type() {
        return "session.renamed";
    }
}
