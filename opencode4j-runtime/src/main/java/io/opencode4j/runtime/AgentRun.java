package io.opencode4j.runtime;

import java.util.concurrent.CompletableFuture;

/**
 * Handle to an active agent execution turn.
 */
public interface AgentRun {

    /** Unique correlation identifier for this run. */
    String runId();

    /** OpenCode session identifier where the run executes. */
    String sessionId();

    /** Future completing with the terminal {@link AgentResult}. */
    CompletableFuture<AgentResult> result();

    /**
     * Blocks until the run completes, fails, or is interrupted.
     *
     * @return the terminal agent result
     */
    AgentResult await();

    /**
     * Interrupts or cancels this active run.
     */
    void cancel();
}
