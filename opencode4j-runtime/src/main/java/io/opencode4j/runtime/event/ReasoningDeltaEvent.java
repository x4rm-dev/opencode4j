package io.opencode4j.runtime.event;

/**
 * Incremental thinking or reasoning delta produced by reasoning models.
 */
public record ReasoningDeltaEvent(
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
