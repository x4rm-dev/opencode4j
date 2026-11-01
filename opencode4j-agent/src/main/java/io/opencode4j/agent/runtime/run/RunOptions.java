package io.opencode4j.agent.runtime.run;

import io.opencode4j.agent.runtime.observe.RuntimeMetrics;
import io.opencode4j.runtime.TextRedactor;

/**
 * Cross-cutting settings of a run.
 *
 * @param awaitServerIdle wait (up to 10 s) for the server to finish an execution still running in the session,
 *                        for example after a previous run was cancelled locally, before prompting
 * @param metrics         where the finished run is reported
 * @param redactor        applied to event payloads and result text before they leave the run
 */
public record RunOptions(boolean awaitServerIdle, RuntimeMetrics metrics, TextRedactor redactor) {

    public RunOptions {
        metrics = metrics == null ? RuntimeMetrics.noop() : metrics;
        redactor = redactor == null ? TextRedactor.none() : redactor;
    }

    public static RunOptions defaults() {
        return new RunOptions(false, null, null);
    }

    public RunOptions awaitingServerIdle(boolean await) {
        return new RunOptions(await, metrics, redactor);
    }
}
