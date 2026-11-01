package io.opencode4j.event;

import io.opencode4j.model.ErrorInfo;
import java.util.Locale;

/**
 * A session execution finished. This is the event that marks the end of an agent turn.
 *
 * @param detail the failure message or interruption reason when the server sent one, otherwise {@code null}
 * @param error  the structured failure of a {@code failed} execution, otherwise {@code null}
 */
public record ExecutionEnded(
        String sessionId,
        ExecutionOutcome outcome,
        String detail,
        ErrorInfo error
) implements OpenCodeEvent {

    /** An end without a structured error. */
    public ExecutionEnded(String sessionId, ExecutionOutcome outcome, String detail) {
        this(sessionId, outcome, detail, null);
    }

    @Override
    public String type() {
        return "session.execution." + outcome.name().toLowerCase(Locale.ROOT);
    }
}
