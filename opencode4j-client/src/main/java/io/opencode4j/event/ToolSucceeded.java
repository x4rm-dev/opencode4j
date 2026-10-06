package io.opencode4j.event;

/** A tool call finished; {@code output} is the text content of its result. */
public record ToolSucceeded(
        String sessionId,
        String messageId,
        String callId,
        String output
) implements OpenCodeEvent {

    @Override
    public String type() {
        return "session.tool.success";
    }
}
