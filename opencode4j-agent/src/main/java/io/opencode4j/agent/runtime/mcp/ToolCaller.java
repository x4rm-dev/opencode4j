package io.opencode4j.agent.runtime.mcp;

/**
 * Who made a tool call.
 *
 * @param runId         the agent run the call belongs to, or the server's own id when unknown
 * @param sessionId     the session that issued the call (a sub-agent's child session for delegated work)
 * @param rootSessionId the root session of that session's tree, for example the conversation's session
 * @param agentName     the calling agent, or {@code ""} when unknown
 * @param agentPath     the calling agent's path, for example {@code "boss/worker"}, or {@code ""} when unknown
 */
public record ToolCaller(String runId, String sessionId, String rootSessionId, String agentName, String agentPath) {
}
