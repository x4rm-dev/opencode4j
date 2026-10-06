package io.opencode4j.runtime.spec;

/**
 * Common marker for tools made available to an agent spec.
 */
public sealed interface ToolSpec permits JavaMethodToolSpec, McpToolSpec {

    /** Name of the tool or tool group. */
    String name();

    /** Brief description of the tool capability. */
    String description();
}
