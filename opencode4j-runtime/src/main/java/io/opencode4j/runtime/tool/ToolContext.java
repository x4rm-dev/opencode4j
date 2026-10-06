package io.opencode4j.runtime.tool;

/**
 * Execution context supplied to a running {@link Tool} method if declared in its parameter list.
 */
public interface ToolContext {

    /** Current run identifier. */
    String runId();

    /** OpenCode session identifier that issued this call. */
    String sessionId();

    /** Name of the agent making the call. */
    String agentName();

    /** Full agent path in the hierarchy (e.g. {@code "boss/worker"}). */
    String agentPath();

    /** Checks whether the execution has been cancelled or timed out. */
    boolean isCancelled();
}
