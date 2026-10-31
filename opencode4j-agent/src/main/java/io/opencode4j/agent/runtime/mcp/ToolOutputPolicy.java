package io.opencode4j.agent.runtime.mcp;

/**
 * Rewrites a tool's text output before it reaches the model, for example to cap its size or to replace a large
 * result with a reference to a stored artifact.
 */
@FunctionalInterface
public interface ToolOutputPolicy {

    /** The text the model receives for {@code output} produced by {@code toolName} for {@code caller}. */
    String apply(String toolName, ToolCaller caller, String output);

    /** Passes output through unchanged. */
    static ToolOutputPolicy unchanged() {
        return (toolName, caller, output) -> output;
    }

    /** Truncates output beyond {@code maxChars} characters and says so. */
    static ToolOutputPolicy truncateAt(int maxChars) {
        return (toolName, caller, output) -> output == null || output.length() <= maxChars
                ? output
                : output.substring(0, maxChars) + "\n[output truncated: " + output.length() + " characters, showing "
                        + maxChars + "]";
    }
}
