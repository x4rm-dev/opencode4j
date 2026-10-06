package io.opencode4j.runtime.event;

/**
 * A tool call has started or its input arguments have begun streaming.
 */
public record ToolCallStartedEvent(
        String runId,
        String sessionId,
        String agentName,
        String agentPath,
        long timestamp,
        String callId,
        String toolName,
        String arguments
) implements AgentEvent {
}
