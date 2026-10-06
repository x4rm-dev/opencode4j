package io.opencode4j.runtime.event;

/**
 * A single LLM execution step has started.
 */
public record StepStartedEvent(
        String runId,
        String sessionId,
        String agentName,
        String agentPath,
        long timestamp,
        String messageId,
        String model
) implements AgentEvent {
}
