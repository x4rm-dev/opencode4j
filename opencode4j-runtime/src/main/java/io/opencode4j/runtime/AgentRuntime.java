package io.opencode4j.runtime;

import io.opencode4j.runtime.spec.AgentSpec;

/**
 * Transport-agnostic agent runtime engine.
 *
 * <p>Provides lifecycle management for preparing workspaces, deploying tools, establishing sessions,
 * and orchestrating hierarchical or multi-agent executions.</p>
 */
public interface AgentRuntime extends AutoCloseable {

    /**
     * Prepares the environment for the specified agent tree and opens an execution session.
     *
     * @param spec root agent specification
     * @return an active session ready to execute turns
     */
    AgentSession open(AgentSpec spec);

    /**
     * Shuts down the runtime, releasing any managed servers, tool ports, or workspaces.
     */
    @Override
    void close();
}
