package io.opencode4j.runtime;

import io.opencode4j.runtime.spec.AgentSpec;

/**
 * An established conversational session bound to an {@link AgentSpec}.
 */
public interface AgentSession extends AutoCloseable {

    /** Session identifier allocated by the underlying server. */
    String sessionId();

    /** The agent specification governing this session. */
    AgentSpec spec();

    /**
     * Sends a simple text prompt to the agent without a custom event listener.
     *
     * @param message prompt message
     * @return handle to the executing run
     */
    default AgentRun send(String message) {
        return send(RunPrompt.of(message), null);
    }

    /**
     * Sends a simple text prompt to the agent with a custom event listener.
     *
     * @param message  prompt message
     * @param listener event listener receiving real-time domain events
     * @return handle to the executing run
     */
    default AgentRun send(String message, AgentEventListener listener) {
        return send(RunPrompt.of(message), listener);
    }

    /**
     * Sends a configured prompt turn to the agent.
     *
     * @param prompt   prompt turn parameters, attachments, and metadata
     * @param listener event listener receiving real-time domain events
     * @return handle to the executing run
     */
    AgentRun send(RunPrompt prompt, AgentEventListener listener);

    /**
     * Closes this session, interrupting any active run and cleaning up session state.
     */
    @Override
    void close();
}
