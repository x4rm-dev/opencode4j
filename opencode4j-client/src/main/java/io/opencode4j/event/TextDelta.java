package io.opencode4j.event;

/** A chunk of assistant text. {@code ordinal} orders text blocks within one message. */
public record TextDelta(
        String sessionId,
        String messageId,
        int ordinal,
        String delta
) implements OpenCodeEvent {

    @Override
    public String type() {
        return "session.text.delta";
    }
}
