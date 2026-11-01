package io.opencode4j.agent.runtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.opencode4j.OpenCodeException;
import io.opencode4j.OpenCodeNotFoundException;
import io.opencode4j.agent.runtime.mcp.JvmMcpServer;
import io.opencode4j.agent.runtime.mcp.ToolCallResolver;
import io.opencode4j.agent.runtime.workspace.LocationManager;
import io.opencode4j.agent.runtime.workspace.WorkspaceWriter;
import io.opencode4j.client.OpenCodeClient;
import io.opencode4j.model.CreateSessionRequest;
import io.opencode4j.model.LocationRef;
import io.opencode4j.model.ModelRef;
import io.opencode4j.model.Session;
import io.opencode4j.runtime.AgentRuntime;
import io.opencode4j.runtime.AgentSession;
import io.opencode4j.runtime.SessionOptions;
import io.opencode4j.runtime.Workspace;
import io.opencode4j.runtime.WorkspaceValidation;
import io.opencode4j.runtime.spec.AgentSpec;
import io.opencode4j.runtime.spec.DynamicToolSpec;
import io.opencode4j.runtime.spec.JavaMethodToolSpec;
import io.opencode4j.runtime.spec.McpToolSpec;
import io.opencode4j.runtime.spec.ProviderSpec;
import io.opencode4j.runtime.spec.WorkspaceSpec;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;

/**
 * Production implementation of {@link AgentRuntime} backed by an OpenCode server.
 *
 * <p>Workspaces are materialized once and shared: {@link #materialize(WorkspaceSpec)} writes a location
 * directory (skipped when its content is unchanged), warms it, and returns a {@link Workspace} in which any number
 * of sessions can be {@linkplain #open(Workspace, SessionOptions) opened} or
 * {@linkplain #resume(Workspace, String, SessionOptions) resumed}. Closing a session never evicts the location;
 * {@link Workspace#retire()} does, once no session uses it.</p>
 *
 * <p>{@link #open(AgentSpec)} remains for single-session use: it materializes a private workspace that is
 * retired, and its server session deleted, when the session closes.</p>
 */
@Slf4j
public final class OpenCodeAgentRuntime implements AgentRuntime {

    private static final Duration DEFAULT_WARMUP_TIMEOUT = Duration.ofSeconds(15);
    private static final String JVM_TOOLS_SERVER_NAME = "jvm_tools";
    private static final String ROOT_AGENT_METADATA = "rootAgent";

    private final OpenCodeClient client;
    private final Path baseWorkspacesDir;
    private final WorkspaceWriter workspaceWriter;
    private final LocationManager locationManager;
    private final ObjectMapper mapper;
    private final Duration warmupTimeout;
    private final Set<OpenCodeAgentSession> openSessions = ConcurrentHashMap.newKeySet();
    private final Map<String, OpenCodeWorkspace> workspaces = new ConcurrentHashMap<>();
    private final Object materializeLock = new Object();
    private final boolean deleteWorkspaceOnClose;
    private final List<ProviderSpec> providers;
    private final RunRegistry runs;
    private final WorkspaceValidator validator;

    @lombok.Builder(builderClassName = "Builder", builderMethodName = "")
    public OpenCodeAgentRuntime(
            OpenCodeClient client,
            Path baseWorkspacesDir,
            Duration warmupTimeout,
            boolean deleteWorkspaceOnClose,
            List<ProviderSpec> providers
    ) {
        this.client = Objects.requireNonNull(client, "client");
        this.baseWorkspacesDir = baseWorkspacesDir != null ? baseWorkspacesDir : defaultTempDir();
        this.warmupTimeout = warmupTimeout != null ? warmupTimeout : DEFAULT_WARMUP_TIMEOUT;
        this.deleteWorkspaceOnClose = deleteWorkspaceOnClose;
        this.providers = providers == null ? List.of() : List.copyOf(providers);
        this.workspaceWriter = new WorkspaceWriter();
        this.locationManager = new LocationManager(client);
        this.mapper = new ObjectMapper();
        this.runs = new RunRegistry(() -> client.eventBus().sessionTree());
        this.validator = new WorkspaceValidator(client);
    }

    public static Builder builder(OpenCodeClient client) {
        return new Builder(client);
    }

    public static OpenCodeAgentRuntime of(OpenCodeClient client) {
        return builder(client).build();
    }

    /**
     * Resolves the session of an in-JVM tool call to its run, root session and agent. Pass it to a shared
     * {@link JvmMcpServer} so its tools receive a complete {@link io.opencode4j.runtime.tool.ToolContext}.
     */
    public ToolCallResolver toolCallResolver() {
        return runs;
    }

    @Override
    public Workspace materialize(WorkspaceSpec spec) {
        Objects.requireNonNull(spec, "spec");
        synchronized (materializeLock) {
            return materialize(spec, false, false);
        }
    }

    @Override
    public WorkspaceValidation validate(Workspace workspace) {
        return validate(workspace, WorkspaceValidator.RESTRICTED_TOOLS);
    }

    /**
     * Like {@link #validate(Workspace)}, checking that {@code restrictedTools} are unavailable to every agent that
     * does not allow them explicitly.
     */
    public WorkspaceValidation validate(Workspace workspace, Set<String> restrictedTools) {
        return validator.validate(own(workspace), Set.copyOf(restrictedTools));
    }

    @Override
    public AgentSession open(Workspace workspace, SessionOptions options) {
        OpenCodeWorkspace target = own(workspace);
        SessionOptions effective = options == null ? SessionOptions.defaults() : options;
        return createSession(target, effective, false);
    }

    @Override
    public AgentSession resume(Workspace workspace, String sessionId, SessionOptions options) {
        OpenCodeWorkspace target = own(workspace);
        Objects.requireNonNull(sessionId, "sessionId");
        Session session = client.sessions().get(sessionId);
        if (session == null) {
            throw new OpenCodeNotFoundException("Session not found: " + sessionId, 404, null, null, null);
        }
        if (session.location() != null && session.location().directory() != null
                && !sameDirectory(session.location().directory(), target.directory())) {
            throw new IllegalArgumentException("Session " + sessionId + " belongs to location "
                    + session.location().directory() + ", not workspace " + target.id());
        }
        log.info("Resumed OpenCode session {} in workspace {}", sessionId, target.id());
        return attach(target, sessionId, options == null ? SessionOptions.defaults() : options, false);
    }

    @Override
    public AgentSession open(AgentSpec spec) {
        Objects.requireNonNull(spec, "spec");
        validateSpec(flattenAgents(spec).toList());
        WorkspaceSpec privateSpec = WorkspaceSpec.builder(privateWorkspaceId(spec.name()), spec)
                .permissions(List.of())
                .allowInlineSecrets(true)
                .build();
        OpenCodeWorkspace workspace = null;
        try {
            synchronized (materializeLock) {
                workspace = materialize(privateSpec, true, deleteWorkspaceOnClose);
            }
            return createSession(workspace, new SessionOptions(null, Map.of(), true), true);
        } catch (RuntimeException e) {
            if (workspace != null && workspace.openSessionCount() == 0) {
                workspace.retire();
            } else if (deleteWorkspaceOnClose) {
                WorkspaceCleaner.deleteQuietly(baseWorkspacesDir.resolve(privateSpec.id()));
            }
            if (e instanceof OpenCodeException openCodeException) {
                throw openCodeException;
            }
            throw new IllegalStateException("Failed to open agent session for '" + spec.name() + "': " + e.getMessage(), e);
        }
    }

    /** Called with {@link #materializeLock} held. */
    private OpenCodeWorkspace materialize(WorkspaceSpec spec, boolean withoutDefaults, boolean deleteOnRetire) {
        List<AgentSpec> agents = flattenAgents(spec.rootAgent()).toList();
        Set<String> agentNames = validateSpec(agents);
        OpenCodeWorkspace existing = workspaces.get(spec.id());
        if (existing != null && !existing.isRetired()) {
            WorkspaceWriter.Rendered again = render(spec, existing.toolServer(), withoutDefaults);
            if (again.hash().equals(existing.contentHash())) {
                return existing;
            }
            if (existing.openSessionCount() > 0) {
                throw new IllegalStateException("Workspace " + spec.id() + " has open sessions and a different spec; "
                        + "materialize the new spec under a new id");
            }
            existing.retire();
        }

        JvmMcpServer toolServer = null;
        try {
            toolServer = createToolServer(spec.rootAgent(), agents);
            WorkspaceWriter.Rendered rendered = render(spec, toolServer, withoutDefaults);
            WorkspaceWriter.Written written = workspaceWriter.write(baseWorkspacesDir.resolve(spec.id()), rendered);
            if (written.changed() && existing != null) {
                // The server may still hold the previous configuration of this directory.
                locationManager.evictLocation(written.directory());
            }
            locationManager.warmLocation(written.directory(), agentNames, warmupTimeout);
            Map<String, String> unavailableMcp = locationManager.awaitMcpServers(written.directory(),
                    mcpServerNames(spec, agents, toolServer), warmupTimeout);
            OpenCodeWorkspace workspace = new OpenCodeWorkspace(spec, written.directory(), written.hash(), toolServer,
                    locationManager, deleteOnRetire, retired -> workspaces.remove(retired.id(), retired), unavailableMcp);
            workspaces.put(spec.id(), workspace);
            log.info("Materialized workspace {} at {} ({}{})", spec.id(), written.directory(), written.hash(),
                    written.changed() ? "" : ", unchanged");
            return workspace;
        } catch (IOException | InterruptedException | RuntimeException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            if (toolServer != null) {
                toolServer.close();
            }
            if (e instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new IllegalStateException("Failed to materialize workspace " + spec.id() + ": " + e.getMessage(), e);
        }
    }

    private static Set<String> mcpServerNames(WorkspaceSpec spec, List<AgentSpec> agents, JvmMcpServer toolServer) {
        Set<String> names = new java.util.LinkedHashSet<>();
        spec.mcpServers().forEach(server -> names.add(server.name()));
        agents.stream().flatMap(agent -> agent.tools().stream())
                .filter(McpToolSpec.class::isInstance)
                .map(tool -> ((McpToolSpec) tool).name())
                .forEach(names::add);
        if (toolServer != null) {
            names.add(JVM_TOOLS_SERVER_NAME);
        }
        return names;
    }

    private WorkspaceWriter.Rendered render(WorkspaceSpec spec, JvmMcpServer toolServer, boolean withoutDefaults) {
        WorkspaceSpec withProviders = withoutDefaults && spec.providers().isEmpty() && !providers.isEmpty()
                ? withProviders(spec) : spec;
        Map<String, McpToolSpec> extraMcp = toolServer == null ? Map.of()
                : Map.of(JVM_TOOLS_SERVER_NAME, toolServer.asMcpToolSpec(JVM_TOOLS_SERVER_NAME));
        return withoutDefaults
                ? workspaceWriter.renderWithoutDefaults(withProviders, extraMcp)
                : workspaceWriter.render(withProviders, extraMcp);
    }

    /** The runtime-level providers apply to single-agent workspaces, as before shared workspaces existed. */
    private WorkspaceSpec withProviders(WorkspaceSpec spec) {
        return new WorkspaceSpec(spec.id(), spec.rootAgent(), providers, spec.mcpServers(), spec.model(),
                spec.smallModel(), spec.instructions(), spec.files(), spec.permissions(), spec.policies(),
                spec.plugins(), spec.enabledProviders(), spec.autoCompaction(), spec.allowInlineSecrets());
    }

    private OpenCodeAgentSession createSession(OpenCodeWorkspace workspace, SessionOptions options,
                                               boolean privateWorkspace) {
        AgentSpec root = workspace.spec().rootAgent();
        ModelRef model = root.model() != null && !root.model().isBlank() ? ModelRef.parse(root.model()) : null;
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put(ROOT_AGENT_METADATA, root.name());
        metadata.putAll(options.metadata());
        String title = options.title() != null && !options.title().isBlank() ? options.title() : root.name();
        Session session = client.sessions().create(new CreateSessionRequest(
                title, root.name(), model, new LocationRef(workspace.directory().toString()), metadata));
        log.info("Created OpenCode session {} for agent '{}' in workspace {}", session.id(), root.name(),
                workspace.directory());
        return attach(workspace, session.id(), options, privateWorkspace);
    }

    private OpenCodeAgentSession attach(OpenCodeWorkspace workspace, String sessionId, SessionOptions options,
                                        boolean privateWorkspace) {
        OpenCodeAgentSession session = new OpenCodeAgentSession(sessionId, workspace.spec().rootAgent(), client,
                workspace, options, runs, openSessions::remove, privateWorkspace);
        workspace.attach(session);
        openSessions.add(session);
        return session;
    }

    private OpenCodeWorkspace own(Workspace workspace) {
        Objects.requireNonNull(workspace, "workspace");
        if (!(workspace instanceof OpenCodeWorkspace owned) || workspaces.get(owned.id()) != owned) {
            throw new IllegalArgumentException("Workspace " + workspace.id()
                    + " was not materialized by this runtime or has been retired");
        }
        return owned;
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
        List<DynamicToolSpec> dynamicTools = agents.stream()
                .flatMap(agent -> agent.tools().stream())
                .filter(DynamicToolSpec.class::isInstance)
                .map(DynamicToolSpec.class::cast)
                .toList();
        if (javaTools.isEmpty() && dynamicTools.isEmpty() && spec.resultSpec() == null) {
            return null;
        }
        return JvmMcpServer.builder()
                .javaTools(javaTools)
                .dynamicTools(dynamicTools)
                .resultSpec(spec.resultSpec())
                .mapper(mapper)
                .resolver(runs)
                .build();
    }

    private static String privateWorkspaceId(String agentName) {
        String safe = agentName.replaceAll("[^A-Za-z0-9._-]", "-").replaceAll("^\\.+", "");
        return (safe.isEmpty() ? "agent" : safe) + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    private static boolean sameDirectory(String reported, Path workspace) {
        try {
            Path candidate = Path.of(reported);
            return Files.exists(candidate) ? candidate.toRealPath().equals(workspace.toRealPath())
                    : candidate.toAbsolutePath().normalize().equals(workspace.toAbsolutePath().normalize());
        } catch (IOException e) {
            return false;
        }
    }

    private static Path defaultTempDir() {
        try {
            return Files.createTempDirectory("opencode4j-workspaces-");
        } catch (IOException e) {
            throw new IllegalStateException("Failed to create default workspaces directory", e);
        }
    }

    /**
     * Closes every session this runtime opened (deleting the ones opened to be deleted on close, detaching the
     * rest) and retires its workspaces.
     */
    @Override
    public void close() {
        for (OpenCodeAgentSession session : new ArrayList<>(openSessions)) {
            try {
                session.close();
            } catch (Exception e) {
                log.warn("Error closing session {}: {}", session.sessionId(), e.getMessage());
            }
        }
        openSessions.clear();
        for (OpenCodeWorkspace workspace : new ArrayList<>(workspaces.values())) {
            try {
                workspace.retire();
            } catch (RuntimeException e) {
                log.warn("Error retiring workspace {}: {}", workspace.id(), e.getMessage());
            }
        }
    }

    public static final class Builder {
        private OpenCodeClient client;

        public Builder(OpenCodeClient client) {
            this.client = Objects.requireNonNull(client, "client");
        }

    }
}
