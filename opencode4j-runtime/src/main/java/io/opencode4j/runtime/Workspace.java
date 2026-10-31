package io.opencode4j.runtime;

import io.opencode4j.runtime.spec.WorkspaceSpec;
import java.nio.file.Path;

/**
 * A materialized OpenCode location that many sessions share. Obtain one from
 * {@link AgentRuntime#materialize(WorkspaceSpec)}.
 */
public interface Workspace {

    /** The spec's id. */
    String id();

    /** The canonical workspace directory; the OpenCode location of every session opened in it. */
    Path directory();

    /** The spec this workspace was materialized from. */
    WorkspaceSpec spec();

    /** SHA-256 of the generated files; materializing an identical spec again is a no-op. */
    String contentHash();

    /** Sessions currently open in this workspace. */
    int openSessionCount();

    /**
     * Releases the location on the server and any tool server the workspace owns. Call it when no session of
     * this workspace will be used again, for example when a release is superseded.
     *
     * @throws IllegalStateException if sessions are still open
     */
    void retire();
}
