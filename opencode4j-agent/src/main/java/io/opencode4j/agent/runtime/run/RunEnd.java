package io.opencode4j.agent.runtime.run;

import io.opencode4j.event.ExecutionEnded;
import io.opencode4j.model.ErrorInfo;
import io.opencode4j.model.message.IdleMessage;
import io.opencode4j.runtime.FailureCategory;
import io.opencode4j.runtime.RunStatus;

/**
 * How and why a run ended.
 *
 * @param interrupted whether listeners see the end as an interruption ({@code RunInterruptedEvent}) rather
 *                    than a completion or failure
 */
record RunEnd(RunStatus status, String reason, FailureCategory category, Long retryAfterMs, boolean interrupted) {

    static RunEnd succeeded() {
        return new RunEnd(RunStatus.SUCCEEDED, null, null, null, false);
    }

    static RunEnd failed(FailureCategory category, String reason) {
        return new RunEnd(RunStatus.FAILED, reason, category, null, false);
    }

    static RunEnd cancelled(String reason) {
        return new RunEnd(RunStatus.CANCELLED, reason, null, null, true);
    }

    static RunEnd partial(FailureCategory category, String reason) {
        return new RunEnd(RunStatus.PARTIAL, reason, category, null, true);
    }

    /** The end the server reported for the root execution. */
    static RunEnd of(ExecutionEnded ended) {
        return switch (ended.outcome()) {
            case SUCCEEDED -> succeeded();
            case FAILED -> fromError(ended.error(), ended.detail() != null ? ended.detail() : "Execution failed", false);
            case INTERRUPTED -> new RunEnd(RunStatus.FAILED,
                    "Execution interrupted by the server" + (ended.detail() != null ? ": " + ended.detail() : ""),
                    FailureCategory.EXECUTION, null, true);
        };
    }

    /** The end recorded in the session history. */
    static RunEnd of(IdleMessage idle) {
        String outcome = idle.outcome() == null ? "" : idle.outcome();
        return switch (outcome) {
            case "succeeded" -> succeeded();
            case "interrupted" -> new RunEnd(RunStatus.FAILED, "Execution interrupted (recovered from session history)",
                    FailureCategory.EXECUTION, null, true);
            default -> fromError(idle.error(), "Execution " + outcome + " (recovered from session history)", false);
        };
    }

    private static RunEnd fromError(ErrorInfo error, String fallbackReason, boolean interrupted) {
        if (error == null) {
            return new RunEnd(RunStatus.FAILED, fallbackReason, FailureCategory.EXECUTION, null, interrupted);
        }
        String reason = error.message() != null ? error.message() : fallbackReason;
        return new RunEnd(RunStatus.FAILED, reason, FailureCategory.fromErrorType(error.type(), error.status()),
                error.retryAfterMs(), interrupted);
    }
}
