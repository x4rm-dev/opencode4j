package io.opencode4j.event;

import io.opencode4j.model.TokenUsage;

/** Cumulative token usage and cost of a session after a step. */
public record SessionUsageUpdated(
        String sessionId,
        TokenUsage usage
) implements OpenCodeEvent {

    @Override
    public String type() {
        return "session.usage.updated";
    }
}
