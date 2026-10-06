package io.opencode4j.event;

/** An event type this library does not model, or a line that could not be parsed, with its raw JSON. */
public record UnknownEvent(
        String type,
        String rawJson
) implements OpenCodeEvent {
}
