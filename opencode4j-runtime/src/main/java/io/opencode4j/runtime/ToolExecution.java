package io.opencode4j.runtime;

/**
 * Summary record of a tool execution during an agent run.
 *
 * @param callId     call identifier
 * @param toolName   unmasked readable tool name
 * @param agentPath  hierarchical path of the agent calling the tool
 * @param arguments  serialized JSON input arguments
 * @param result     result text or error output
 * @param isError    true if tool failed
 * @param durationMs duration of the tool execution in milliseconds
 */
public record ToolExecution(
        String callId,
        String toolName,
        String agentPath,
        String arguments,
        String result,
        boolean isError,
        long durationMs
) {
}
