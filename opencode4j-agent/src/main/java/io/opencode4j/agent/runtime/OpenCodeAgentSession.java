package io.opencode4j.agent.runtime;

import io.opencode4j.agent.runtime.mcp.JvmMcpServer;
import io.opencode4j.agent.runtime.run.OpenCodeAgentRun;
import io.opencode4j.agent.runtime.workspace.LocationManager;
import io.opencode4j.client.OpenCodeClient;
import io.opencode4j.runtime.AgentEventListener;
import io.opencode4j.runtime.AgentRun;
import io.opencode4j.runtime.AgentSession;
import io.opencode4j.runtime.RunPrompt;
import io.opencode4j.runtime.spec.AgentSpec;
import java.nio.file.Path;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * An established conversational session backed by an OpenCode session and dedicated workspace location.
 */
public final class OpenCodeAgentSession implements AgentSession {

    private static final Logger log = LoggerFactory.getLogger(OpenCodeAgentSession.class);

    private final String sessionId;
    private final AgentSpec spec;
    private final OpenCodeClient client;
    private final Path workspaceDir;
    private final LocationManager locationManager;
    private final JvmMcpServer jvmMcpServer;
    private final boolean deleteWorkspaceOnClose;

    private final AtomicReference<OpenCodeAgentRun> currentRun = new AtomicReference<>();

    public OpenCodeAgentSession(
            String sessionId,
            AgentSpec spec,
            OpenCodeClient client,
            Path workspaceDir,
            LocationManager locationManager,
            JvmMcpServer jvmMcpServer
    ) {
        this(sessionId, spec, client, workspaceDir, locationManager, jvmMcpServer, false);
    }

    public OpenCodeAgentSession(
            String sessionId,
            AgentSpec spec,
            OpenCodeClient client,
            Path workspaceDir,
            LocationManager locationManager,
            JvmMcpServer jvmMcpServer,
            boolean deleteWorkspaceOnClose
    ) {
        this.deleteWorkspaceOnClose = deleteWorkspaceOnClose;
        this.sessionId = Objects.requireNonNull(sessionId, "sessionId");
        this.spec = Objects.requireNonNull(spec, "spec");
        this.client = Objects.requireNonNull(client, "client");
        this.workspaceDir = Objects.requireNonNull(workspaceDir, "workspaceDir");
        this.locationManager = Objects.requireNonNull(locationManager, "locationManager");
        this.jvmMcpServer = jvmMcpServer;
    }

    @Override
    public String sessionId() {
        return sessionId;
    }

    @Override
    public AgentSpec spec() {
        return spec;
    }

    public Path workspaceDir() {
        return workspaceDir;
    }

    @Override
    public AgentRun send(RunPrompt prompt, AgentEventListener listener) {
        Objects.requireNonNull(prompt, "prompt");

        String runId = "run_" + UUID.randomUUID().toString().substring(0, 12);
        OpenCodeAgentRun run = new OpenCodeAgentRun(
                runId,
                sessionId,
                spec,
                prompt,
                listener,
                client,
                workspaceDir,
                jvmMcpServer
        );

        currentRun.set(run);
        run.start();
        return run;
    }

    @Override
    public void close() {
        OpenCodeAgentRun active = currentRun.getAndSet(null);
        if (active != null) {
            active.cancel();
        }

        // Delete session from server (cascades to child sub-agent sessions, F15)
        try {
            client.sessions().delete(sessionId);
            log.debug("Deleted OpenCode session {}", sessionId);
        } catch (Exception e) {
            log.debug("Session {} cleanup: {}", sessionId, e.getMessage());
        }

        // Evict location from server cache
        locationManager.evictLocation(workspaceDir);

        // Close in-JVM MCP server
        if (jvmMcpServer != null) {
            jvmMcpServer.close();
        }

        if (deleteWorkspaceOnClose) {
            WorkspaceCleaner.deleteQuietly(workspaceDir);
        }
    }
}
