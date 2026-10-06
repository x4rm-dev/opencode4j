package io.opencode4j.runtime.event;

/**
 * An agent turn or run was interrupted (e.g. timeout, budget exceeded, or user cancellation).
 */
public record RunInterruptedEvent(
        String runId,
        String sessionId,
        String agentName,
        String agentPath,
        long timestamp,
        String reason
) implements AgentEvent {
}
