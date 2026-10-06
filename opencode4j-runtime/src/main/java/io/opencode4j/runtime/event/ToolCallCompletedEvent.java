package io.opencode4j.runtime.event;

/**
 * A tool call completed successfully with output text or data.
 */
public record ToolCallCompletedEvent(
        String runId,
        String sessionId,
        String agentName,
        String agentPath,
        long timestamp,
        String callId,
        String toolName,
        String output
) implements AgentEvent {
}
