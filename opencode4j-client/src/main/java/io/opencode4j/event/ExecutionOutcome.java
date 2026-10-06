package io.opencode4j.event;

/** How a session execution (one agent turn, possibly many steps) ended. */
public enum ExecutionOutcome {

    SUCCEEDED,
    FAILED,
    INTERRUPTED
}
