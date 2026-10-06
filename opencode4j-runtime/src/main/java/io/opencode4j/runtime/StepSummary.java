package io.opencode4j.runtime;

/**
 * Summary record of a single step within an agent run.
 *
 * @param ordinal      step index (1-based)
 * @param agentPath    hierarchical path of the agent executing this step
 * @param finishReason finish reason reported by model (e.g. {@code "stop"}, {@code "tool-calls"})
 * @param tokens       token usage for this step
 * @param cost         calculated cost in USD for this step
 */
public record StepSummary(
        int ordinal,
        String agentPath,
        String finishReason,
        TokenUsage tokens,
        double cost
) {
}
