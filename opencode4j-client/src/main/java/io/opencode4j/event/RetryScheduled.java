package io.opencode4j.event;

import io.opencode4j.model.ErrorInfo;

/** A failed model call will be retried at {@code at} (epoch ms), for example after a provider rate limit. */
public record RetryScheduled(
        String sessionId,
        String messageId,
        int attempt,
        long at,
        ErrorInfo error
) implements OpenCodeEvent {

    @Override
    public String type() {
        return "session.retry.scheduled";
    }
}
