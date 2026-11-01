package io.opencode4j.event;

import io.opencode4j.model.ErrorInfo;

import io.opencode4j.model.TokenUsage;

/** Context compaction failed. */
public record CompactionFailed(
        String sessionId,
        String reason,
        String inputId,
        ErrorInfo error,
        TokenUsage usage
) implements OpenCodeEvent {

    @Override
    public String type() {
        return "session.compaction.failed";
    }
}
