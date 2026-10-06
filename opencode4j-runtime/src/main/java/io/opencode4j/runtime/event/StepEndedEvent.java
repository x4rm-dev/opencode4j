package io.opencode4j.runtime.event;

import io.opencode4j.runtime.TokenUsage;

/**
 * A single LLM step finished with finish status, token usage, and cost.
 */
public record StepEndedEvent(
        String runId,
        String sessionId,
        String agentName,
        String agentPath,
        long timestamp,
        String messageId,
        String finishReason,
        TokenUsage tokens,
        double cost
) implements AgentEvent {
}
