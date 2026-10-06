package io.opencode4j.event;

import java.util.Locale;

/**
 * A session execution finished. This is the event that marks the end of an agent turn.
 *
 * @param detail the failure message or interruption reason when the server sent one, otherwise {@code null}
 */
public record ExecutionEnded(
        String sessionId,
        ExecutionOutcome outcome,
        String detail
) implements OpenCodeEvent {

    @Override
    public String type() {
        return "session.execution." + outcome.name().toLowerCase(Locale.ROOT);
    }
}
