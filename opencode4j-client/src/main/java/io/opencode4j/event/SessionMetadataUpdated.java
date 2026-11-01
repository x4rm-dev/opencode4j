package io.opencode4j.event;

import java.util.Map;

/** A session's metadata changed; {@code metadata} is the new value. */
public record SessionMetadataUpdated(
        String sessionId,
        Map<String, Object> metadata
) implements OpenCodeEvent {

    @Override
    public String type() {
        return "session.metadata.updated";
    }
}
