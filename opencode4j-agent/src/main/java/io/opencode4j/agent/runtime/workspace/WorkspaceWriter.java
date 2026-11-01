package io.opencode4j.agent.runtime.workspace;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.opencode4j.runtime.spec.AgentSpec;
import io.opencode4j.runtime.spec.McpToolSpec;
import io.opencode4j.runtime.spec.PermissionRule;
import io.opencode4j.runtime.spec.ProviderSpec;
import io.opencode4j.runtime.spec.SkillSpec;
import io.opencode4j.runtime.spec.WorkspaceSpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

/**
 * Generates a workspace directory: {@code opencode.json}, {@code .opencode/skills/<name>/SKILL.md}, instruction
 * files and extra files.
 *
 * <p>Files are rendered in memory first and identified by a SHA-256 content hash stored in
 * {@code .opencode4j/hash}; writing an identical workspace again changes nothing on disk. Each file is written
 * through a temporary file and an atomic move, so OpenCode never reads a half-written config. On POSIX file
 * systems directories are created {@code rwx------} and files {@code rw-------}.</p>
 */
@Slf4j
public final class WorkspaceWriter {

    static final String CONFIG_FILE = "opencode.json";
    static final String HASH_FILE = ".opencode4j/hash";
    static final String INSTRUCTIONS_DIR = ".opencode/instructions/";
    private static final String CONFIG_SCHEMA = "https://opencode.ai/config.json";

    private static final ObjectMapper MAPPER = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
    private static final boolean POSIX = FileSystems.getDefault().supportedFileAttributeViews().contains("posix");
    private static final Set<PosixFilePermission> DIRECTORY_PERMISSIONS = PosixFilePermissions.fromString("rwx------");
    private static final Set<PosixFilePermission> FILE_PERMISSIONS = PosixFilePermissions.fromString("rw-------");

    /**
     * The files of a workspace, keyed by path relative to the workspace, and their combined hash.
     *
     * @param files ordered by path
     */
    public record Rendered(Map<String, byte[]> files, String hash) {
    }

    /**
     * The outcome of writing a workspace.
     *
     * @param directory canonical workspace directory
     * @param hash      content hash of the files
     * @param changed   whether anything was written; {@code false} if the directory already held this content
     */
    public record Written(Path directory, String hash, boolean changed) {
    }

    /**
     * Writes out the complete workspace environment for the provided agent spec.
     *
     * @param workspaceDir    target directory to initialize
     * @param rootSpec        root agent hierarchy specification
     * @param extraMcpServers optional runtime-managed MCP servers (e.g. in-JVM tool server)
     * @return the canonical real path of the workspace
     * @throws IOException if directory creation or file writing fails
     */
    public Path write(
            Path workspaceDir,
            AgentSpec rootSpec,
            Map<String, McpToolSpec> extraMcpServers
    ) throws IOException {
        return write(workspaceDir, rootSpec, extraMcpServers, List.of());
    }

    /**
     * Same as {@link #write(Path, AgentSpec, Map)} but also declares custom LLM providers. Provider header
     * suppliers are evaluated here, once per call, so credentials are fresh for each written workspace.
     *
     * <p>This single-agent form keeps OpenCode's own permission defaults; workspaces built from a
     * {@link WorkspaceSpec} deny everything not explicitly allowed.</p>
     *
     * @param providers custom OpenAI-compatible providers to declare in the workspace config
     */
    public Path write(
            Path workspaceDir,
            AgentSpec rootSpec,
            Map<String, McpToolSpec> extraMcpServers,
            List<ProviderSpec> providers
    ) throws IOException {
        Objects.requireNonNull(rootSpec, "rootSpec");
        WorkspaceSpec legacy = WorkspaceSpec.builder("legacy", rootSpec)
                .permissions(List.of())
                .allowInlineSecrets(true)
                .build();
        return write(workspaceDir, render(legacy, providers, extraMcpServers, false)).directory();
    }

    /**
     * Renders {@code spec} into files without touching the disk.
     *
     * @param extraMcpServers runtime-managed MCP servers, such as a workspace's in-JVM tool server
     */
    public Rendered render(WorkspaceSpec spec, Map<String, McpToolSpec> extraMcpServers) {
        return render(spec, spec.providers(), extraMcpServers, true);
    }

    /**
     * Renders {@code spec} without workspace-wide defaults (no root permission, schema, share or update settings),
     * as the single-agent {@link #write(Path, AgentSpec, Map, List)} form does.
     */
    public Rendered renderWithoutDefaults(WorkspaceSpec spec, Map<String, McpToolSpec> extraMcpServers) {
        return render(spec, spec.providers(), extraMcpServers, false);
    }

    /** Writes rendered files into {@code workspaceDir} unless it already holds exactly this content. */
    public Written write(Path workspaceDir, Rendered rendered) throws IOException {
        Objects.requireNonNull(workspaceDir, "workspaceDir");
        createDirectories(workspaceDir);
        Path canonicalDir = workspaceDir.toRealPath();
        Path hashFile = canonicalDir.resolve(HASH_FILE);
        if (Files.exists(hashFile) && rendered.hash().equals(Files.readString(hashFile, StandardCharsets.UTF_8).trim())) {
            log.debug("Workspace {} is up to date ({})", canonicalDir, rendered.hash());
            return new Written(canonicalDir, rendered.hash(), false);
        }
        for (Map.Entry<String, byte[]> file : rendered.files().entrySet()) {
            writeAtomically(canonicalDir.resolve(file.getKey()), file.getValue());
        }
        writeAtomically(hashFile, rendered.hash().getBytes(StandardCharsets.UTF_8));
        log.debug("Wrote workspace {} ({} files, {})", canonicalDir, rendered.files().size(), rendered.hash());
        return new Written(canonicalDir, rendered.hash(), true);
    }

    private Rendered render(WorkspaceSpec spec, List<ProviderSpec> providers, Map<String, McpToolSpec> extraMcpServers,
                            boolean workspaceDefaults) {
        Map<String, byte[]> files = new TreeMap<>();
        val agents = spec.rootAgent().allAgents();
        for (AgentSpec agent : agents) {
            for (SkillSpec skill : agent.skills()) {
                files.put(".opencode/skills/" + skill.name() + "/SKILL.md", utf8(skillMarkdown(skill)));
            }
        }
        spec.instructions().forEach((name, content) -> files.put(INSTRUCTIONS_DIR + name, utf8(content)));
        spec.files().forEach((path, content) -> files.put(path, utf8(content)));
        val config = createConfiguration(spec, agents, providers, extraMcpServers, workspaceDefaults);
        try {
            files.put(CONFIG_FILE, MAPPER.writeValueAsBytes(config));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to render opencode.json", e);
        }
        return new Rendered(Collections.unmodifiableMap(files), hash(files));
    }

    private WorkspaceConfiguration createConfiguration(
            WorkspaceSpec spec, List<AgentSpec> agents, List<ProviderSpec> providers,
            Map<String, McpToolSpec> extraMcpServers, boolean workspaceDefaults) {
        val rootSpec = spec.rootAgent();
        // RunLimits.maxDepth caps how deep sub-agents may nest; without it the tree's own depth is allowed.
        val depth = rootSpec.limits().maxDepth() != null ? rootSpec.limits().maxDepth() : rootSpec.calculateDepth();
        val agentConfig = new LinkedHashMap<String, WorkspaceConfiguration.Agent>();
        // OpenCode lets workspace-level rules override agent rules, so a workspace-wide deny-all would hide even
        // the tools an agent allows. Workspace rules therefore lead each agent's own rules instead.
        List<PermissionRule> workspaceRules = workspaceDefaults ? spec.permissions() : List.of();
        agents.forEach(agent -> agentConfig.put(agent.name(),
                toAgentConfiguration(agent, rootSpec.name(), workspaceRules)));
        val servers = new LinkedHashMap<String, WorkspaceConfiguration.McpServer>();
        collectMcpServers(agents, spec.mcpServers(), extraMcpServers)
                .forEach((name, server) -> servers.put(name, toMcpConfiguration(server)));
        val builder = WorkspaceConfiguration.builder()
                .experimental(depth >= 2
                        ? WorkspaceConfiguration.Experimental.builder().subagentDepth(depth).build() : null)
                .provider(toProviderConfiguration(providers))
                .agent(agentConfig)
                .mcp(servers.isEmpty() ? null : WorkspaceConfiguration.Mcp.builder()
                        .servers(servers).build());
        if (workspaceDefaults) {
            builder.schema(CONFIG_SCHEMA)
                    .model(nonBlank(spec.model()))
                    .smallModel(nonBlank(spec.smallModel()))
                    .instructions(spec.instructions().isEmpty() ? null : spec.instructions().keySet().stream()
                            .sorted().map(name -> INSTRUCTIONS_DIR + name).toList())
                    .policies(spec.policies().isEmpty() ? null : spec.policies().stream()
                            .map(policy -> new WorkspaceConfiguration.Policy(policy.action(), policy.resource(),
                                    policy.effect()))
                            .toList())
                    .plugin(spec.plugins())
                    .enabledProviders(spec.enabledProviders().isEmpty() ? null : spec.enabledProviders())
                    .share("disabled")
                    .autoupdate(false)
                    .compaction(spec.autoCompaction() == null ? null
                            : new WorkspaceConfiguration.Compaction(spec.autoCompaction()));
        }
        return builder.build();
    }

    private Map<String, WorkspaceConfiguration.Provider> toProviderConfiguration(List<ProviderSpec> providers) {
        if (providers == null || providers.isEmpty()) {
            return null;
        }
        val result = new LinkedHashMap<String, WorkspaceConfiguration.Provider>();
        providers.forEach(provider -> result.put(provider.id(), WorkspaceConfiguration.Provider.builder()
                .npm("@ai-sdk/openai-compatible")
                .name(provider.name())
                .options(WorkspaceConfiguration.ProviderOptions.builder()
                        .baseUrl(provider.baseUrl())
                        .apiKey(provider.apiKey() == null ? null : provider.apiKey().render())
                        .headers(providerHeaders(provider))
                        .build())
                .models(toModelConfiguration(provider.models()))
                .build()));
        return result;
    }

    private Map<String, WorkspaceConfiguration.ProviderModel> toModelConfiguration(
            List<ProviderSpec.ModelSpec> models) {
        val result = new LinkedHashMap<String, WorkspaceConfiguration.ProviderModel>();
        models.forEach(model -> result.put(model.id(), WorkspaceConfiguration.ProviderModel.builder()
                .id(model.id())
                .name(model.name())
                .toolCall(model.toolCalling())
                .modalities(new WorkspaceConfiguration.Modalities(
                        model.vision() ? List.of("text", "image") : List.of("text"), List.of("text")))
                .limit(model.contextTokens() > 0 && model.outputTokens() > 0
                        ? new WorkspaceConfiguration.Limit(model.contextTokens(), model.outputTokens())
                        : null)
                .cost(model.cost() == null ? null : new WorkspaceConfiguration.Cost(
                        model.cost().inputPerMillion(), model.cost().outputPerMillion()))
                .build()));
        return result;
    }

    private static Map<String, String> providerHeaders(ProviderSpec provider) {
        Map<String, String> headers = new LinkedHashMap<>(provider.headers().get());
        provider.headerRefs().forEach((name, value) -> headers.put(name, value.render()));
        return headers.isEmpty() ? null : headers;
    }

    private WorkspaceConfiguration.Agent toAgentConfiguration(AgentSpec agent, String rootName,
                                                              List<PermissionRule> workspaceRules) {
        List<PermissionRule> rules = new java.util.ArrayList<>(workspaceRules);
        rules.addAll(agent.permissions());
        return WorkspaceConfiguration.Agent.builder()
                .mode(agent.name().equals(rootName) ? "primary" : "subagent")
                .description(nonBlank(agent.description()))
                .prompt(nonBlank(agent.instruction()))
                .model(nonBlank(agent.model()))
                .steps(agent.effectiveSteps())
                .permission(toPermissionMap(rules))
                .build();
    }

    private Map<String, Object> toPermissionMap(List<PermissionRule> permissions) {
        if (permissions == null || permissions.isEmpty()) {
            return null;
        }
        val map = new LinkedHashMap<String, Object>();
        for (val rule : permissions) {
            String effect = rule.effect().wireValue();
            if ("*".equals(rule.resource())) {
                map.put(rule.action(), effect);
            } else {
                @SuppressWarnings("unchecked")
                Map<String, String> sub = (Map<String, String>) map.computeIfAbsent(rule.action(), k -> new LinkedHashMap<String, String>());
                sub.put(rule.resource(), effect);
            }
        }
        return map;
    }

    private String nonBlank(String value) {
        return value != null && !value.isBlank() ? value : null;
    }

    private Map<String, McpToolSpec> collectMcpServers(
            List<AgentSpec> agents, List<McpToolSpec> sharedServers, Map<String, McpToolSpec> extraMcpServers) {
        val allMcp = new LinkedHashMap<String, McpToolSpec>();
        if (extraMcpServers != null) {
            allMcp.putAll(extraMcpServers);
        }
        sharedServers.forEach(server -> allMcp.put(server.name(), server));
        agents.stream()
                .flatMap(agent -> agent.tools().stream())
                .filter(McpToolSpec.class::isInstance)
                .map(McpToolSpec.class::cast)
                .forEachOrdered(mcp -> allMcp.put(mcp.name(), mcp));
        return allMcp;
    }

    private WorkspaceConfiguration.McpServer toMcpConfiguration(McpToolSpec server) {
        val builder = WorkspaceConfiguration.McpServer.builder()
                .type(server.type());

        if ("local".equals(server.type())) {
            return builder.command(server.command())
                    .environment(server.environment())
                    .build();
        }
        return builder.url(server.url())
                .headers(server.headers())
                .build();
    }

    private static String skillMarkdown(SkillSpec skill) {
        return "---\n"
                + "name: " + skill.name() + "\n"
                + "description: " + skill.description() + "\n"
                + "---\n\n"
                + skill.content() + "\n";
    }

    private static byte[] utf8(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }

    private static String hash(Map<String, byte[]> files) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (Map.Entry<String, byte[]> file : files.entrySet()) {
                digest.update(utf8(file.getKey()));
                digest.update((byte) 0);
                digest.update(file.getValue());
                digest.update((byte) 0);
            }
            return "sha256:" + HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private static void createDirectories(Path directory) throws IOException {
        if (Files.isDirectory(directory)) {
            return;
        }
        Path parent = directory.toAbsolutePath().getParent();
        if (parent != null) {
            createDirectories(parent);
        }
        try {
            if (POSIX) {
                Files.createDirectory(directory, PosixFilePermissions.asFileAttribute(DIRECTORY_PERMISSIONS));
            } else {
                Files.createDirectory(directory);
            }
        } catch (FileAlreadyExistsException ignored) {
            // Created concurrently.
        }
    }

    private static void writeAtomically(Path target, byte[] content) throws IOException {
        createDirectories(target.getParent());
        Path temporary = POSIX
                ? Files.createTempFile(target.getParent(), ".tmp-", ".part",
                        PosixFilePermissions.asFileAttribute(FILE_PERMISSIONS))
                : Files.createTempFile(target.getParent(), ".tmp-", ".part");
        try {
            Files.write(temporary, content);
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }
}
