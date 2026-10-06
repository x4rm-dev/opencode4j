package io.opencode4j.model;

/** Lifecycle of a tool call observed while an agent runs. */
public enum ToolStatus {

    /** The model is still streaming the tool input. */
    PREPARING,
    /** The tool has been called and is executing. */
    RUNNING,
    /** The tool finished successfully. */
    COMPLETED,
    /** The tool failed or was interrupted. */
    FAILED
}
