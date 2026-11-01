package io.opencode4j.event;

/** Context compaction started; {@code reason} is {@code auto} or {@code manual}. */
public record CompactionStarted(
        String sessionId,
        String reason,
        String inputId
) implements OpenCodeEvent {

    @Override
    public String type() {
        return "session.compaction.started";
    }
}
