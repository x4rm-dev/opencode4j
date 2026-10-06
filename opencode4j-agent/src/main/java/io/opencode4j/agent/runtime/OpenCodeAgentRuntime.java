package io.opencode4j.agent.runtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.opencode4j.agent.runtime.mcp.JvmMcpServer;
import io.opencode4j.agent.runtime.workspace.LocationManager;
import io.opencode4j.agent.runtime.workspace.WorkspaceWriter;
import io.opencode4j.client.OpenCodeClient;
import io.opencode4j.model.CreateSessionRequest;
import io.opencode4j.model.LocationRef;
import io.opencode4j.model.ModelRef;
import io.opencode4j.model.Session;
import io.opencode4j.runtime.AgentRuntime;
import io.opencode4j.runtime.AgentSession;
import io.opencode4j.runtime.spec.AgentSpec;
import io.opencode4j.runtime.spec.JavaMethodToolSpec;
import io.opencode4j.runtime.spec.McpToolSpec;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;

/**
 * Production implementation of {@link AgentRuntime} backed by an OpenCode server.
 *
 * <p>Each opened session is allocated an isolated workspace directory with generated {@code opencode.json},
 * dynamically spawned in-JVM tool servers, location warm-up polling, and full lifecycle tracking.</p>
 */
@Slf4j
public final class OpenCodeAgentRuntime implements AgentRuntime {

    private static final Duration DEFAULT_WARMUP_TIMEOUT = Duration.ofSeconds(15);
    private static final String JVM_TOOLS_SERVER_NAME = "jvm_tools";

    private final OpenCodeClient client;
    private final Path baseWorkspacesDir;
    private final WorkspaceWriter workspaceWriter;
    private final LocationManager locationManager;
    private final ObjectMapper mapper;
    private final Duration warmupTimeout;
    private final List<OpenCodeAgentSession> openSessions = Collections.synchronizedList(new ArrayList<>());
    private final boolean deleteWorkspaceOnClose;

    @lombok.Builder(builderClassName = "Builder", builderMethodName = "")
    public OpenCodeAgentRuntime(
            OpenCodeClient client,
            Path baseWorkspacesDir,
            Duration warmupTimeout,
            boolean deleteWorkspaceOnClose
    ) {
        this.client = Objects.requireNonNull(client, "client");
        this.baseWorkspacesDir = baseWorkspacesDir != null ? baseWorkspacesDir : defaultTempDir();
        this.warmupTimeout = warmupTimeout != null ? warmupTimeout : DEFAULT_WARMUP_TIMEOUT;
        this.deleteWorkspaceOnClose = deleteWorkspaceOnClose;
        this.workspaceWriter = new WorkspaceWriter();
        this.locationManager = new LocationManager(client);
        this.mapper = new ObjectMapper();
    }

    public static Builder builder(OpenCodeClient client) {
        return new Builder(client);
    }

    public static OpenCodeAgentRuntime of(OpenCodeClient client) {
        return builder(client).build();
    }

    @Override
    public AgentSession open(AgentSpec spec) {
        Objects.requireNonNull(spec, "spec");

        List<AgentSpec> agents = flattenAgents(spec).toList();
        Set<String> agentNames = validateSpec(agents);

        String sessionWorkspaceName = spec.name() + "-" + UUID.randomUUID().toString().substring(0, 8);
        Path sessionDir = baseWorkspacesDir.resolve(sessionWorkspaceName);

        JvmMcpServer jvmMcpServer = null;
        try {
            jvmMcpServer = createToolServer(spec, agents);
            Map<String, McpToolSpec> extraMcp = jvmMcpServer == null ? Map.of() : Map.of(
                    JVM_TOOLS_SERVER_NAME, jvmMcpServer.asMcpToolSpec(JVM_TOOLS_SERVER_NAME));
            Path canonicalDir = workspaceWriter.write(sessionDir, spec, extraMcp);
            locationManager.warmLocation(canonicalDir, agentNames, warmupTimeout);
            OpenCodeAgentSession agentSession = createSession(spec, canonicalDir, jvmMcpServer);
            openSessions.add(agentSession);
            return agentSession;
        } catch (IOException | InterruptedException | RuntimeException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            if (jvmMcpServer != null) {
                jvmMcpServer.close();
            }
            throw new IllegalStateException("Failed to open agent session for '" + spec.name() + "': " + e.getMessage(), e);
        }
    }

    private static Stream<AgentSpec> flattenAgents(AgentSpec spec) {
        return Stream.concat(Stream.of(spec), spec.subAgents().stream().flatMap(OpenCodeAgentRuntime::flattenAgents));
    }

    private Set<String> validateSpec(List<AgentSpec> agents) {
        Set<String> names = agents.stream().map(AgentSpec::name).collect(Collectors.toSet());
        if (names.size() != agents.size()) {
            throw new IllegalArgumentException("Duplicate agent names detected in agent spec tree: " + names);
        }
        return names;
    }

    private JvmMcpServer createToolServer(AgentSpec spec, List<AgentSpec> agents) throws IOException {
        List<JavaMethodToolSpec> javaTools = agents.stream()
                .flatMap(agent -> agent.tools().stream())
                .filter(JavaMethodToolSpec.class::isInstance)
                .map(JavaMethodToolSpec.class::cast)
                .toList();
        if (javaTools.isEmpty() && spec.resultSpec() == null) {
            return null;
        }
        String runId = "tools-" + UUID.randomUUID().toString().substring(0, 8);
        return new JvmMcpServer(runId, javaTools, spec.resultSpec(), mapper);
    }

    private OpenCodeAgentSession createSession(AgentSpec spec, Path directory, JvmMcpServer toolServer) {
        ModelRef model = spec.model() != null && !spec.model().isBlank()
                ? ModelRef.parse(spec.model())
                : null;
        Session session = client.sessions().create(new CreateSessionRequest(
                spec.name(), spec.name(), model, new LocationRef(directory.toString()),
                Map.of("rootAgent", spec.name())));
        log.info("Created OpenCode session {} for agent '{}' in workspace {}", session.id(), spec.name(), directory);
        return new OpenCodeAgentSession(session.id(), spec, client, directory, locationManager, toolServer);
    }

    private static Path defaultTempDir() {
        try {
            return Files.createTempDirectory("opencode4j-workspaces-");
        } catch (IOException e) {
            throw new IllegalStateException("Failed to create default workspaces directory", e);
        }
    }

    @Override
    public void close() {
        synchronized (openSessions) {
            for (OpenCodeAgentSession session : openSessions) {
                try {
                    session.close();
                } catch (Exception e) {
                    log.warn("Error closing session {}: {}", session.sessionId(), e.getMessage());
                }
            }
            openSessions.clear();
        }
    }

    public static final class Builder {
        private OpenCodeClient client;

        public Builder(OpenCodeClient client) {
            this.client = Objects.requireNonNull(client, "client");
        }

    }
}
