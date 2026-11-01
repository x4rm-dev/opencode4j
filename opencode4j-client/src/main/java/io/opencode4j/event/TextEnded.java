package io.opencode4j.event;

/** A text part finished; {@code text} is the complete text of the part. */
public record TextEnded(
        String sessionId,
        String messageId,
        int ordinal,
        String text
) implements OpenCodeEvent {

    @Override
    public String type() {
        return "session.text.ended";
    }
}
