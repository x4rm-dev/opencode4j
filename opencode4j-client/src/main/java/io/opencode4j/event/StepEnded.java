package io.opencode4j.event;

import io.opencode4j.model.TokenUsage;

/** One model step finished. {@code finish} is the finish reason, for example {@code stop} or {@code tool-calls}. */
public record StepEnded(
        String sessionId,
        String messageId,
        String finish,
        TokenUsage usage
) implements OpenCodeEvent {

    @Override
    public String type() {
        return "session.step.ended";
    }
}
