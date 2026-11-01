package io.opencode4j.agent.runtime.observe;

import io.opencode4j.runtime.FailureCategory;
import io.opencode4j.runtime.RunStatus;
import io.opencode4j.runtime.TokenUsage;

/**
 * The measurable facts of one finished run.
 *
 * @param model           the root agent's {@code provider/model}, or {@code null} for the workspace default
 * @param failureCategory why it did not succeed, or {@code null}
 * @param steps           model steps across the session tree
 * @param toolCalls       tool calls across the session tree
 */
public record RunSummary(
        String runId,
        String sessionId,
        String agentName,
        String model,
        RunStatus status,
        FailureCategory failureCategory,
        long durationMillis,
        TokenUsage tokens,
        double cost,
        int steps,
        int toolCalls
) {
}
