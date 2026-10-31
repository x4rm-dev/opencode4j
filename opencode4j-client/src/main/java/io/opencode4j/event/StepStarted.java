package io.opencode4j.event;

import io.opencode4j.model.ModelRef;

/** A model step started for the given agent and model. */
public record StepStarted(
        String sessionId,
        String messageId,
        String agent,
        ModelRef model
) implements OpenCodeEvent {

    @Override
    public String type() {
        return "session.step.started";
    }
}
