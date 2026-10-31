package io.opencode4j.event;

import io.opencode4j.model.TokenUsage;

/** Context compaction completed; {@code summary} is the text that replaces the compacted history. */
public record CompactionEnded(
        String sessionId,
        String reason,
        String summary,
        TokenUsage usage
) implements OpenCodeEvent {

    @Override
    public String type() {
        return "session.compaction.ended";
    }
}
