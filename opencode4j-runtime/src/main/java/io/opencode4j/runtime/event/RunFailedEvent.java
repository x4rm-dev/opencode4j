package io.opencode4j.runtime.event;

/**
 * An agent turn or run failed with an error.
 */
public record RunFailedEvent(
        String runId,
        String sessionId,
        String agentName,
        String agentPath,
        long timestamp,
        String error
) implements AgentEvent {
}
