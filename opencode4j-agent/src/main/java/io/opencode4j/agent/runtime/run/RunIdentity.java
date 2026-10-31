package io.opencode4j.agent.runtime.run;

/**
 * Which run and agent a session belongs to.
 *
 * @param agentPath full agent path in the hierarchy, for example {@code "boss/worker"}
 */
public record RunIdentity(String runId, String rootSessionId, String agentName, String agentPath) {
}
