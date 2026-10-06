package io.opencode4j.event;

/**
 * A typed event from the OpenCode server-sent event stream ({@code GET /api/event}).
 *
 * <p>This is a sealed hierarchy; pattern-match with {@code switch}. Event types this library does not model
 * surface as {@link UnknownEvent} carrying the raw JSON, so a newer server never breaks the stream.</p>
 */
public sealed interface OpenCodeEvent
        permits TextDelta, ReasoningDelta, ToolStarted, ToolCalled, ToolSucceeded, ToolFailed,
        StepEnded, StepFailed, ExecutionStarted, ExecutionEnded, PermissionAsked, FormCreated,
        SessionCreated, UnknownEvent {

    /** The server's event type, for example {@code "session.text.delta"}. */
    String type();
}
