package io.opencode4j.runtime.event;

/**
 * A tool call failed with an error.
 */
public record ToolCallFailedEvent(
        String runId,
        String sessionId,
        String agentName,
        String agentPath,
        long timestamp,
        String callId,
        String toolName,
        String error
) implements AgentEvent {
}
