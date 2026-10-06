package io.opencode4j.runtime.event;

/**
 * Incremental streaming text chunk produced by the agent.
 */
public record TextDeltaEvent(
        String runId,
        String sessionId,
        String agentName,
        String agentPath,
        long timestamp,
        String messageId,
        int ordinal,
        String delta
) implements AgentEvent {
}
