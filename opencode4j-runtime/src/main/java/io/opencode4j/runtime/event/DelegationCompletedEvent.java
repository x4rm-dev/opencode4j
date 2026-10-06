package io.opencode4j.runtime.event;

/**
 * A sub-agent delegation completed.
 */
public record DelegationCompletedEvent(
        String runId,
        String sessionId,
        String agentName,
        String agentPath,
        long timestamp,
        String childSessionId,
        String targetAgentName,
        boolean success,
        String result
) implements AgentEvent {
}
