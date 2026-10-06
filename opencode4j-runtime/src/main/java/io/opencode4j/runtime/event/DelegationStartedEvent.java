package io.opencode4j.runtime.event;

/**
 * An agent delegated a task to a sub-agent, creating or invoking a child session.
 */
public record DelegationStartedEvent(
        String runId,
        String sessionId,
        String agentName,
        String agentPath,
        long timestamp,
        String childSessionId,
        String targetAgentName,
        String prompt,
        String description
) implements AgentEvent {
}
