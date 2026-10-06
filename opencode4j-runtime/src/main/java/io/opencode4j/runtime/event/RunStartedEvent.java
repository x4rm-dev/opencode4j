package io.opencode4j.runtime.event;

/**
 * A user turn or run has started executing.
 */
public record RunStartedEvent(
        String runId,
        String sessionId,
        String agentName,
        String agentPath,
        long timestamp,
        String prompt
) implements AgentEvent {
}
