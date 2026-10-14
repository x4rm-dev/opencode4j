package io.opencode4j.agent.runtime.workspace;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.opencode4j.runtime.spec.AgentSpec;
import io.opencode4j.runtime.spec.McpToolSpec;
import io.opencode4j.runtime.spec.ProviderSpec;
import io.opencode4j.runtime.spec.SkillSpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

/**
 * Writes the per-session workspace directory structure containing {@code opencode.json}
 * and {@code .opencode/skills/<name>/SKILL.md} skill definitions.
 */
@Slf4j
public final class WorkspaceWriter {

    private static final ObjectMapper MAPPER = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

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
     * @param providers custom OpenAI-compatible providers to declare in the workspace config
     */
    public Path write(
            Path workspaceDir,
            AgentSpec rootSpec,
            Map<String, McpToolSpec> extraMcpServers,
            List<ProviderSpec> providers
    ) throws IOException {
        Objects.requireNonNull(workspaceDir, "workspaceDir");
        Objects.requireNonNull(rootSpec, "rootSpec");

        Files.createDirectories(workspaceDir);
        val canonicalDir = workspaceDir.toRealPath();
        val agents = rootSpec.allAgents();
        val config = createConfiguration(rootSpec, agents, extraMcpServers, providers);
        val configFile = canonicalDir.resolve("opencode.json");
        MAPPER.writeValue(configFile.toFile(), config);
        log.debug("Wrote workspace configuration to {}", configFile);

        for (AgentSpec agent : agents) {
            for (SkillSpec skill : agent.skills()) {
                writeSkill(canonicalDir, skill);
            }
        }
        return canonicalDir;
    }

    private WorkspaceConfiguration createConfiguration(
            AgentSpec rootSpec, List<AgentSpec> agents, Map<String, McpToolSpec> extraMcpServers,
            List<ProviderSpec> providers) {
        val depth = rootSpec.calculateDepth();
        val agentConfig = new LinkedHashMap<String, WorkspaceConfiguration.Agent>();
        agents.forEach(agent -> agentConfig.put(agent.name(), toAgentConfiguration(agent, rootSpec.name())));
        val servers = new LinkedHashMap<String, WorkspaceConfiguration.McpServer>();
        collectMcpServers(agents, extraMcpServers)
                .forEach((name, server) -> servers.put(name, toMcpConfiguration(server)));
        return WorkspaceConfiguration.builder()
                .experimental(depth >= 2
                        ? WorkspaceConfiguration.Experimental.builder().subagentDepth(depth).build() : null)
                .provider(toProviderConfiguration(providers))
                .agent(agentConfig)
                .mcp(servers.isEmpty() ? null : WorkspaceConfiguration.Mcp.builder()
                        .servers(servers).build())
                .build();
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
                        .headers(provider.headers().get())
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
                .attachment(model.vision())
                .modalities(new WorkspaceConfiguration.Modalities(
                        model.vision() ? List.of("text", "image") : List.of("text"), List.of("text")))
                .limit(model.contextTokens() > 0 && model.outputTokens() > 0
                        ? new WorkspaceConfiguration.Limit(model.contextTokens(), model.outputTokens())
                        : null)
                .build()));
        return result;
    }

    private WorkspaceConfiguration.Agent toAgentConfiguration(AgentSpec agent, String rootName) {
        return WorkspaceConfiguration.Agent.builder()
                .mode(agent.name().equals(rootName) ? "primary" : "subagent")
                .description(nonBlank(agent.description()))
                .prompt(nonBlank(agent.instruction()))
                .model(nonBlank(agent.model()))
                .steps(agent.effectiveSteps())
                .permission(toPermissionMap(agent.permissions()))
                .build();
    }

    private Map<String, Object> toPermissionMap(List<io.opencode4j.runtime.spec.PermissionRule> permissions) {
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
            List<AgentSpec> agents, Map<String, McpToolSpec> extraMcpServers) {
        val allMcp = new LinkedHashMap<String, McpToolSpec>();
        if (extraMcpServers != null) {
            allMcp.putAll(extraMcpServers);
        }
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

    private void writeSkill(Path workspaceDir, SkillSpec skill) throws IOException {
        val skillDir = workspaceDir.resolve(".opencode")
                .resolve("skills")
                .resolve(skill.name());

        Files.createDirectories(skillDir);
        val skillFile = skillDir.resolve("SKILL.md");

        val markdown = "---\n"
                + "name: " + skill.name() + "\n"
                + "description: " + skill.description() + "\n"
                + "---\n\n"
                + skill.content() + "\n";

        Files.writeString(skillFile, markdown, StandardCharsets.UTF_8);
        log.debug("Wrote skill markdown to {}", skillFile);
    }
}
