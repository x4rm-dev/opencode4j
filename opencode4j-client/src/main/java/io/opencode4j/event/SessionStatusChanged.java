package io.opencode4j.event;

/** A session's run status changed: {@code idle}, {@code busy} or {@code retry}. For {@code retry}, {@code attempt}, {@code message} and {@code nextAttemptAt} (epoch ms) describe the scheduled retry; otherwise they are {@code null}. */
public record SessionStatusChanged(
        String sessionId,
        String status,
        Integer attempt,
        String message,
        Long nextAttemptAt
) implements OpenCodeEvent {

    @Override
    public String type() {
        return "session.status";
    }
}
