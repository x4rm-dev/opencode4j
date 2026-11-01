package io.opencode4j.runtime.tool;

/**
 * Execution context supplied to a running {@link Tool} method if declared in its parameter list.
 */
public interface ToolContext {

    /** Current run identifier. */
    String runId();

    /** OpenCode session identifier that issued this call. */
    String sessionId();

    /**
     * Root session of the calling session's tree: the session the caller opened, even when a sub-agent's child
     * session made the call. Use it to look up the conversation, user or tenant a call belongs to.
     */
    default String rootSessionId() {
        return sessionId();
    }

    /** Name of the agent making the call. */
    String agentName();

    /** Full agent path in the hierarchy (e.g. {@code "boss/worker"}). */
    String agentPath();

    /** Checks whether the execution has been cancelled or timed out. */
    boolean isCancelled();
}
