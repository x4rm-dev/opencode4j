package io.opencode4j.agent.runtime;

import io.opencode4j.agent.runtime.mcp.JvmMcpServer;
import io.opencode4j.agent.runtime.workspace.LocationManager;
import io.opencode4j.runtime.Workspace;
import io.opencode4j.runtime.spec.WorkspaceSpec;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;

/**
 * A materialized workspace shared by the sessions opened in it. It counts its open sessions so the location is
 * evicted from the server, and its in-JVM tool server closed, only when nothing uses it any more.
 */
@Slf4j
final class OpenCodeWorkspace implements Workspace {

    private final WorkspaceSpec spec;
    private final Path directory;
    private final String contentHash;
    private final JvmMcpServer toolServer;
    private final LocationManager locationManager;
    private final boolean deleteDirectoryOnRetire;
    private final Consumer<OpenCodeWorkspace> onRetire;
    private final Map<String, String> unavailableMcpServers;
    private final Set<OpenCodeAgentSession> sessions = ConcurrentHashMap.newKeySet();
    private boolean retired;

    OpenCodeWorkspace(WorkspaceSpec spec, Path directory, String contentHash, JvmMcpServer toolServer,
                      LocationManager locationManager, boolean deleteDirectoryOnRetire,
                      Consumer<OpenCodeWorkspace> onRetire, Map<String, String> unavailableMcpServers) {
        this.spec = Objects.requireNonNull(spec, "spec");
        this.directory = Objects.requireNonNull(directory, "directory");
        this.contentHash = Objects.requireNonNull(contentHash, "contentHash");
        this.toolServer = toolServer;
        this.locationManager = Objects.requireNonNull(locationManager, "locationManager");
        this.deleteDirectoryOnRetire = deleteDirectoryOnRetire;
        this.onRetire = Objects.requireNonNull(onRetire, "onRetire");
        this.unavailableMcpServers = unavailableMcpServers == null ? Map.of() : Map.copyOf(unavailableMcpServers);
    }

    /** MCP servers that had not connected when the workspace was materialized, with their status. */
    Map<String, String> unavailableMcpServers() {
        return unavailableMcpServers;
    }

    @Override
    public String id() {
        return spec.id();
    }

    @Override
    public Path directory() {
        return directory;
    }

    @Override
    public WorkspaceSpec spec() {
        return spec;
    }

    @Override
    public String contentHash() {
        return contentHash;
    }

    @Override
    public int openSessionCount() {
        return sessions.size();
    }

    /** The in-JVM tool server serving this workspace's Java tools, or {@code null}. */
    JvmMcpServer toolServer() {
        return toolServer;
    }

    synchronized void attach(OpenCodeAgentSession session) {
        if (retired) {
            throw new IllegalStateException("Workspace " + id() + " has been retired");
        }
        sessions.add(session);
    }

    void release(OpenCodeAgentSession session) {
        sessions.remove(session);
    }

    synchronized boolean isRetired() {
        return retired;
    }

    @Override
    public synchronized void retire() {
        if (retired) {
            return;
        }
        if (!sessions.isEmpty()) {
            throw new IllegalStateException("Workspace " + id() + " still has " + sessions.size() + " open sessions");
        }
        retired = true;
        locationManager.evictLocation(directory);
        if (toolServer != null) {
            toolServer.close();
        }
        if (deleteDirectoryOnRetire) {
            WorkspaceCleaner.deleteQuietly(directory);
        }
        onRetire.accept(this);
        log.debug("Retired workspace {} at {}", id(), directory);
    }
}
