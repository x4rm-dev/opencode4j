package io.opencode4j.runtime;

/** How a run ended. */
public enum RunStatus {
    /** The agent finished the turn. */
    SUCCEEDED,
    /** The turn failed; {@link AgentResult#failureCategory()} says why. */
    FAILED,
    /** The caller cancelled the run. */
    CANCELLED,
    /**
     * The run was stopped by a configured limit (budget, steps or wall-clock timeout) before the agent finished;
     * the text and accounting collected so far are kept, so the caller can present partial findings.
     */
    PARTIAL
}
