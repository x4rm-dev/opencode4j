package io.opencode4j.agent.runtime.run;

import io.opencode4j.runtime.StepSummary;
import io.opencode4j.runtime.TokenUsage;
import io.opencode4j.runtime.ToolExecution;
import java.util.ArrayList;
import java.util.List;

/** Mutable accounting accessed only under the run's state lock. */
final class RunAccounting {
    final StringBuilder rootText = new StringBuilder();
    final List<ToolExecution> tools = new ArrayList<>();
    final List<StepSummary> steps = new ArrayList<>();
    TokenUsage usage = TokenUsage.ZERO;
    double cost;
    int rootSteps;

    TokenUsage recordStep(String path, String finish, io.opencode4j.model.TokenUsage reported,
                          double stepCost, boolean root) {
        TokenUsage tokens = reported == null ? TokenUsage.ZERO : new TokenUsage(
                reported.input(), reported.output(), reported.reasoning(), reported.cacheRead(), reported.cacheWrite());
        usage = usage.add(tokens);
        cost += stepCost;
        if (root) {
            rootSteps++;
        }
        steps.add(new StepSummary(steps.size() + 1, path, finish, tokens, stepCost));
        return tokens;
    }
}
