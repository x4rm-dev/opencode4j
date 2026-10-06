package io.opencode4j.event;

/** A chunk of model reasoning. {@code ordinal} orders reasoning blocks within one message. */
public record ReasoningDelta(
        String sessionId,
        String messageId,
        int ordinal,
        String delta
) implements OpenCodeEvent {

    @Override
    public String type() {
        return "session.reasoning.delta";
    }
}
