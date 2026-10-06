package io.opencode4j.event;

import io.opencode4j.model.TokenUsage;

/** One model step failed or was interrupted. {@code errorType} is for example {@code aborted}. */
public record StepFailed(
        String sessionId,
        String messageId,
        String errorType,
        String message,
        TokenUsage usage
) implements OpenCodeEvent {

    @Override
    public String type() {
        return "session.step.failed";
    }
}
