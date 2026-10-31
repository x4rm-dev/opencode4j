package io.opencode4j.runtime;

import io.opencode4j.runtime.spec.AgentSpec;
import java.util.Optional;

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

    /** The run in progress, if any. A session runs one prompt at a time. */
    default Optional<AgentRun> activeRun() {
        return Optional.empty();
    }

    /**
     * Stops observing the session without touching it on the server: an active run ends locally as
     * {@link RunStatus#CANCELLED}, the server keeps the session (and finishes any turn in progress), and it can be
     * resumed later.
     */
    default void detach() {
        close();
    }

    /** Interrupts any active run and deletes the session on the server, including its sub-agent sessions. */
    default void delete() {
        close();
    }

    /**
     * Closes this session: {@link #delete()} if it was opened with {@link SessionOptions#deleteOnClose()}, otherwise
     * {@link #detach()}.
     */
    @Override
    void close();
}
