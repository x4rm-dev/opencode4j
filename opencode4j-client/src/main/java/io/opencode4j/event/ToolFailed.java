package io.opencode4j.event;

/** A tool call failed or was interrupted. */
public record ToolFailed(
        String sessionId,
        String messageId,
        String callId,
        String error
) implements OpenCodeEvent {

    @Override
    public String type() {
        return "session.tool.failed";
    }
}
