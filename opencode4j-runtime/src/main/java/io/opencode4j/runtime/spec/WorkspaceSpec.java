package io.opencode4j.runtime.spec;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Everything one OpenCode location (workspace directory) needs: the agent tree, providers, shared MCP servers,
 * instructions, files and permissions. Materialize it once, for example per use-case release, and open every
 * session of that release in it.
 *
 * <p>Permissions default to deny-all: with no {@code permissions} given, every agent's rules start with
 * {@code "*": "deny"}, so agents only get what their own rules allow. Tools a rule denies outright are not
 * offered to the model at all. MCP tools are permission-checked as {@code <server>_<tool>}; when
 * {@code execute} (OpenCode Code Mode) is denied, allowed MCP tools are offered to the model directly.</p>
 *
 * @param id               caller-chosen identifier, used as the directory below the runtime's workspace root;
 *                         one or more {@code /}-separated segments of letters, digits, {@code .}, {@code _} or
 *                         {@code -}, for example {@code debugging/rel_0042}
 * @param rootAgent        the primary agent and its sub-agents
 * @param providers        custom LLM providers
 * @param mcpServers       MCP servers available to every agent, in addition to those agents declare
 * @param model            default {@code provider/model} for the workspace, or {@code null}
 * @param smallModel       {@code provider/model} for titles and compaction, or {@code null}
 * @param instructions     always-on instruction files: file name to Markdown content
 * @param files            extra files: path relative to the workspace to content, for example agent prompts
 *                         referenced as {@code {file:...}}
 * @param permissions      workspace-wide permission rules, placed before each agent's own rules
 * @param policies         hard server policies
 * @param plugins          OpenCode plugins to load; empty means none
 * @param enabledProviders if non-empty, only these providers may be used
 * @param autoCompaction   whether OpenCode compacts automatically, or {@code null} for its default
 */
public record WorkspaceSpec(
        String id,
        AgentSpec rootAgent,
        List<ProviderSpec> providers,
        List<McpToolSpec> mcpServers,
        String model,
        String smallModel,
        Map<String, String> instructions,
        Map<String, String> files,
        List<PermissionRule> permissions,
        List<PolicyRule> policies,
        List<String> plugins,
        List<String> enabledProviders,
        Boolean autoCompaction
) {

    private static final Pattern ID = Pattern.compile("[A-Za-z0-9._-]+(/[A-Za-z0-9._-]+)*");

    /** The default workspace-wide rule set: deny everything not explicitly allowed. */
    public static final List<PermissionRule> DENY_ALL = List.of(PermissionRule.deny("*", "*"));

    public WorkspaceSpec {
        Objects.requireNonNull(id, "id");
        if (!ID.matcher(id).matches() || id.contains("..")) {
            throw new IllegalArgumentException("Invalid workspace id: " + id);
        }
        Objects.requireNonNull(rootAgent, "rootAgent");
        providers = providers == null ? List.of() : List.copyOf(providers);
        mcpServers = mcpServers == null ? List.of() : List.copyOf(mcpServers);
        instructions = instructions == null ? Map.of() : Map.copyOf(instructions);
        files = files == null ? Map.of() : Map.copyOf(files);
        permissions = permissions == null ? DENY_ALL : List.copyOf(permissions);
        policies = policies == null ? List.of() : List.copyOf(policies);
        plugins = plugins == null ? List.of() : List.copyOf(plugins);
        enabledProviders = enabledProviders == null ? List.of() : List.copyOf(enabledProviders);
        files.keySet().forEach(WorkspaceSpec::requireRelativePath);
        instructions.keySet().forEach(WorkspaceSpec::requireFileName);
    }

    public static Builder builder(String id, AgentSpec rootAgent) {
        return new Builder(id, rootAgent);
    }

    private static void requireRelativePath(String path) {
        if (path.isBlank() || path.startsWith("/") || path.contains("..") || path.contains("\\")) {
            throw new IllegalArgumentException("Workspace file paths must be relative and stay inside it: " + path);
        }
    }

    private static void requireFileName(String name) {
        if (name.isBlank() || name.contains("/") || name.contains("\\") || name.contains("..")) {
            throw new IllegalArgumentException("Instruction names must be plain file names: " + name);
        }
    }

    /** Builder for {@link WorkspaceSpec}. */
    public static final class Builder {
        private final String id;
        private final AgentSpec rootAgent;
        private final List<ProviderSpec> providers = new ArrayList<>();
        private final List<McpToolSpec> mcpServers = new ArrayList<>();
        private String model;
        private String smallModel;
        private final Map<String, String> instructions = new LinkedHashMap<>();
        private final Map<String, String> files = new LinkedHashMap<>();
        private List<PermissionRule> permissions;
        private final List<PolicyRule> policies = new ArrayList<>();
        private final List<String> plugins = new ArrayList<>();
        private final List<String> enabledProviders = new ArrayList<>();
        private Boolean autoCompaction;

        private Builder(String id, AgentSpec rootAgent) {
            this.id = id;
            this.rootAgent = rootAgent;
        }

        public Builder provider(ProviderSpec provider) {
            providers.add(Objects.requireNonNull(provider, "provider"));
            return this;
        }

        public Builder mcpServer(McpToolSpec server) {
            mcpServers.add(Objects.requireNonNull(server, "server"));
            return this;
        }

        public Builder model(String model) {
            this.model = model;
            return this;
        }

        public Builder smallModel(String smallModel) {
            this.smallModel = smallModel;
            return this;
        }

        /** Adds an always-on instruction file written to {@code .opencode/instructions/<name>}. */
        public Builder instruction(String name, String markdown) {
            instructions.put(Objects.requireNonNull(name, "name"), Objects.requireNonNull(markdown, "markdown"));
            return this;
        }

        public Builder file(String relativePath, String content) {
            files.put(Objects.requireNonNull(relativePath, "relativePath"), Objects.requireNonNull(content, "content"));
            return this;
        }

        /** Replaces the default deny-all workspace rules. */
        public Builder permissions(List<PermissionRule> rules) {
            this.permissions = List.copyOf(rules);
            return this;
        }

        public Builder policy(PolicyRule policy) {
            policies.add(Objects.requireNonNull(policy, "policy"));
            return this;
        }

        public Builder plugin(String plugin) {
            plugins.add(Objects.requireNonNull(plugin, "plugin"));
            return this;
        }

        public Builder enabledProvider(String providerId) {
            enabledProviders.add(Objects.requireNonNull(providerId, "providerId"));
            return this;
        }

        public Builder autoCompaction(boolean autoCompaction) {
            this.autoCompaction = autoCompaction;
            return this;
        }

        public WorkspaceSpec build() {
            return new WorkspaceSpec(id, rootAgent, providers, mcpServers, model, smallModel, instructions, files,
                    permissions, policies, plugins, enabledProviders, autoCompaction);
        }
    }
}
