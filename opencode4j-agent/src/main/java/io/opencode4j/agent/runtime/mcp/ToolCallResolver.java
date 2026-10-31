package io.opencode4j.agent.runtime.mcp;

/**
 * Resolves the session id carried by an MCP tool call to the run and agent that made it. The agent runtime
 * provides one backed by its active runs and the event bus's session tree; see
 * {@code OpenCodeAgentRuntime#toolCallResolver()}.
 */
@FunctionalInterface
public interface ToolCallResolver {

    /** Describes the caller of a call from {@code sessionId}; never {@code null}. */
    ToolCaller resolve(String sessionId);

    /** A resolver that knows nothing beyond the session id. */
    static ToolCallResolver unresolved(String runId) {
        return sessionId -> new ToolCaller(runId, sessionId, sessionId, "", "");
    }
}
