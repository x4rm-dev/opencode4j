package io.opencode4j.runtime.spec;

import java.time.Duration;

/**
 * Budget and resource limits enforced across an agent execution.
 *
 * @param maxSteps            maximum step turns allowed for the primary agent (server-enforced)
 * @param maxCost             maximum cumulative cost in USD across all sessions before interrupt
 * @param maxTokens           maximum cumulative tokens across all sessions before interrupt
 * @param timeout             maximum wall-clock duration before run interrupt
 * @param maxParallelChildren maximum concurrent child sub-agent sessions allowed
 * @param maxDepth            maximum sub-agent nesting depth (derived from tree if null)
 */
public record RunLimits(
        Integer maxSteps,
        Double maxCost,
        Integer maxTokens,
        Duration timeout,
        Integer maxParallelChildren,
        Integer maxDepth
) {

    public static final RunLimits UNLIMITED = new RunLimits(null, null, null, null, null, null);

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private Integer maxSteps;
        private Double maxCost;
        private Integer maxTokens;
        private Duration timeout;
        private Integer maxParallelChildren;
        private Integer maxDepth;

        public Builder maxSteps(Integer maxSteps) {
            this.maxSteps = maxSteps;
            return this;
        }

        public Builder maxCost(Double maxCost) {
            this.maxCost = maxCost;
            return this;
        }

        public Builder maxTokens(Integer maxTokens) {
            this.maxTokens = maxTokens;
            return this;
        }

        public Builder timeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        public Builder maxParallelChildren(Integer maxParallelChildren) {
            this.maxParallelChildren = maxParallelChildren;
            return this;
        }

        public Builder maxDepth(Integer maxDepth) {
            this.maxDepth = maxDepth;
            return this;
        }

        public RunLimits build() {
            return new RunLimits(maxSteps, maxCost, maxTokens, timeout, maxParallelChildren, maxDepth);
        }
    }
}
