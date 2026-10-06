package io.opencode4j.event;

/** The model started streaming the input of a tool call. */
public record ToolStarted(
        String sessionId,
        String messageId,
        String callId,
        String name
) implements OpenCodeEvent {

    @Override
    public String type() {
        return "session.tool.input.started";
    }
}
