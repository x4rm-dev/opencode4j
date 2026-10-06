package io.opencode4j.runtime.event;

/**
 * An agent turn or run has completed successfully.
 */
public record RunCompletedEvent(
        String runId,
        String sessionId,
        String agentName,
        String agentPath,
        long timestamp,
        String finalText
) implements AgentEvent {
}
