package io.opencode4j.runtime;

import io.opencode4j.runtime.event.AgentEvent;

/**
 * Consumer interface receiving real-time domain events during an agent run.
 */
@FunctionalInterface
public interface AgentEventListener {

    /**
     * Called whenever a new agent event occurs.
     *
     * @param event the emitted agent event
     */
    void onEvent(AgentEvent event);
}
