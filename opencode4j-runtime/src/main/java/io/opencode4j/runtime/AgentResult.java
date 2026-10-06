package io.opencode4j.runtime;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Terminal outcome and metrics of an agent run.
 *
 * @param runId          correlation run identifier
 * @param sessionId      OpenCode primary session identifier
 * @param success        whether the run completed successfully without fatal error or interrupt
 * @param text           final text response produced by the agent
 * @param typedResult    typed structured result if {@link io.opencode4j.runtime.spec.ResultSpec} was configured
 * @param errorMessage   failure or interrupt explanation if not successful
 * @param truncated      true if the run was cut short by a configured step limit
 * @param tokenUsage     cumulative token usage across the root session and all child sessions
 * @param totalCost      cumulative cost in USD across the entire session tree
 * @param toolExecutions list of all tool calls made during the run
 * @param steps          breakdown of individual steps executed
 */
public record AgentResult(
        String runId,
        String sessionId,
        boolean success,
        String text,
        Object typedResult,
        String errorMessage,
        boolean truncated,
        TokenUsage tokenUsage,
        double totalCost,
        List<ToolExecution> toolExecutions,
        List<StepSummary> steps
) {

    public AgentResult {
        Objects.requireNonNull(runId, "runId");
        Objects.requireNonNull(sessionId, "sessionId");
        text = text == null ? "" : text;
        tokenUsage = tokenUsage == null ? TokenUsage.ZERO : tokenUsage;
        toolExecutions = toolExecutions == null ? List.of() : List.copyOf(toolExecutions);
        steps = steps == null ? List.of() : List.copyOf(steps);
    }

    public boolean isSuccess() {
        return success;
    }

    @SuppressWarnings("unchecked")
    public <T> Optional<T> typedResult(Class<T> type) {
        if (typedResult != null && type.isInstance(typedResult)) {
            return Optional.of((T) typedResult);
        }
        return Optional.empty();
    }

    public static Builder builder(String runId, String sessionId) {
        return new Builder(runId, sessionId);
    }

    public static final class Builder {
        private final String runId;
        private final String sessionId;
        private boolean success;
        private String text = "";
        private Object typedResult;
        private String errorMessage;
        private boolean truncated;
        private TokenUsage tokenUsage = TokenUsage.ZERO;
        private double totalCost;
        private List<ToolExecution> toolExecutions = List.of();
        private List<StepSummary> steps = List.of();

        public Builder(String runId, String sessionId) {
            this.runId = Objects.requireNonNull(runId, "runId");
            this.sessionId = Objects.requireNonNull(sessionId, "sessionId");
        }

        public Builder success(boolean success) {
            this.success = success;
            return this;
        }

        public Builder text(String text) {
            this.text = text;
            return this;
        }

        public Builder typedResult(Object typedResult) {
            this.typedResult = typedResult;
            return this;
        }

        public Builder errorMessage(String errorMessage) {
            this.errorMessage = errorMessage;
            return this;
        }

        public Builder truncated(boolean truncated) {
            this.truncated = truncated;
            return this;
        }

        public Builder tokenUsage(TokenUsage tokenUsage) {
            this.tokenUsage = tokenUsage;
            return this;
        }

        public Builder totalCost(double totalCost) {
            this.totalCost = totalCost;
            return this;
        }

        public Builder toolExecutions(List<ToolExecution> toolExecutions) {
            this.toolExecutions = toolExecutions;
            return this;
        }

        public Builder steps(List<StepSummary> steps) {
            this.steps = steps;
            return this;
        }

        public AgentResult build() {
            return new AgentResult(
                    runId,
                    sessionId,
                    success,
                    text,
                    typedResult,
                    errorMessage,
                    truncated,
                    tokenUsage,
                    totalCost,
                    toolExecutions,
                    steps
            );
        }
    }
}
