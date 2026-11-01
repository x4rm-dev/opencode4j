package io.opencode4j.runtime;

import io.opencode4j.runtime.spec.AgentSpec;
import io.opencode4j.runtime.spec.WorkspaceSpec;

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
     * Writes the files of {@code spec} into its workspace directory and warms the location, unless an identical
     * spec was already materialized. Sessions opened in the returned workspace share it.
     *
     * @throws IllegalStateException if the id is in use by sessions with a different spec
     */
    default Workspace materialize(WorkspaceSpec spec) {
        throw new UnsupportedOperationException("This runtime does not support shared workspaces");
    }

    /**
     * Checks {@code workspace} against the server: every provider is registered, every referenced model is visible
     * (and supports tools where an agent uses it), and no agent can use a restricted tool ({@code shell},
     * {@code execute}, {@code edit}, {@code write}, {@code webfetch}, {@code websearch}, {@code question}) unless
     * its own rules allow it. Call it before routing traffic to a new release.
     */
    default WorkspaceValidation validate(Workspace workspace) {
        throw new UnsupportedOperationException("This runtime does not support workspace validation");
    }

    /** Opens a new session in {@code workspace}. */
    default AgentSession open(Workspace workspace, SessionOptions options) {
        throw new UnsupportedOperationException("This runtime does not support shared workspaces");
    }

    /**
     * Attaches to an existing session of {@code workspace}, for example after an application restart.
     *
     * @throws IllegalArgumentException if the session belongs to another location
     */
    default AgentSession resume(Workspace workspace, String sessionId, SessionOptions options) {
        throw new UnsupportedOperationException("This runtime does not support resuming sessions");
    }

    /**
     * Shuts down the runtime, releasing any managed servers, tool ports, or workspaces.
     */
    @Override
    void close();
}
