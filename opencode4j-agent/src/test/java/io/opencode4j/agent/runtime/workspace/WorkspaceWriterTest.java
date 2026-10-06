package io.opencode4j.agent.runtime.workspace;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.opencode4j.runtime.spec.AgentSpec;
import io.opencode4j.runtime.spec.McpToolSpec;
import io.opencode4j.runtime.spec.PermissionRule;
import io.opencode4j.runtime.spec.SkillSpec;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WorkspaceWriterTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final WorkspaceWriter writer = new WorkspaceWriter();

    @Test
    void omitsUnsetOptionalFields(@TempDir Path tempDir) throws IOException {
        AgentSpec spec = AgentSpec.builder("boss")
                .description(" ")
                .instruction("")
                .steps(0)
                .build();

        Path directory = writer.write(tempDir, spec, null);
        JsonNode config = mapper.readTree(directory.resolve("opencode.json").toFile());
        JsonNode agent = config.path("agent").path("boss");

        assertEquals("primary", agent.path("mode").asText());
        assertEquals(1, agent.size());
        assertFalse(config.has("experimental"));
        assertFalse(config.has("mcp"));
    }

    @Test
    void preservesMcpOverridesAndLocalCommandOrder(@TempDir Path tempDir) throws IOException {
        McpToolSpec local = McpToolSpec.local(
                "shared", "Local tools", List.of("node", "tools.js", "--stdio"), Map.of("MODE", "test"));
        McpToolSpec remote = McpToolSpec.remote("remote", "Remote tools", "https://example.test/mcp");
        AgentSpec spec = AgentSpec.builder("boss")
                .tool(local)
                .subAgent(AgentSpec.builder("worker").tool(remote)
                        .skill(SkillSpec.always("worker-skill", "Worker skill", "Instructions"))
                        .build())
                .build();
        McpToolSpec overridden = McpToolSpec.remote("shared", "Managed tools", "https://example.test/old");

        Path directory = writer.write(tempDir, spec, Map.of("shared", overridden));
        JsonNode config = mapper.readTree(directory.resolve("opencode.json").toFile());
        JsonNode servers = config.path("mcp").path("servers");
        JsonNode localNode = servers.path("shared");

        assertEquals("local", localNode.path("type").asText());
        assertEquals(mapper.valueToTree(local.command()), localNode.path("command"));
        assertEquals("test", localNode.path("environment").path("MODE").asText());
        assertFalse(localNode.has("url"));
        assertEquals(remote.url(), servers.path("remote").path("url").asText());
        assertFalse(servers.path("remote").has("headers"));
        assertFalse(config.has("experimental"));
        assertTrue(Files.exists(directory.resolve(".opencode/skills/worker-skill/SKILL.md")));
    }

    @Test
    void writesValidOpencodeJsonAndSkillFiles(@TempDir Path tempDir) throws IOException {
        AgentSpec worker = AgentSpec.builder("worker")
                .description("Worker subagent")
                .instruction("I do small tasks")
                .build();

        AgentSpec lead = AgentSpec.builder("lead")
                .description("Lead subagent")
                .instruction("I coordinate workers")
                .subAgent(worker)
                .build();

        AgentSpec boss = AgentSpec.builder("boss")
                .description("Top orchestrator")
                .instruction("I delegate work")
                .model("github-copilot/gpt-6.1-sol")
                .steps(15)
                .permission(PermissionRule.deny("*", "*"))
                .permission(PermissionRule.allow("skill", "launch-protocol"))
                .skill(SkillSpec.always("launch-protocol", "Secret launch code", "# Launch Code: 42"))
                .subAgent(lead)
                .build();

        McpToolSpec extraMcp = McpToolSpec.remote(
                "jvm_tools", "In JVM tools", "http://127.0.0.1:54321/mcp",
                Map.of("Authorization", "Bearer tok-test")
        );

        Path canonicalDir = writer.write(tempDir, boss, Map.of("jvm_tools", extraMcp));

        Path configFile = canonicalDir.resolve("opencode.json");
        assertTrue(Files.exists(configFile));

        JsonNode rootNode = mapper.readTree(configFile.toFile());

        // Check experimental subagent_depth derived (boss > lead > worker = depth 2)
        assertEquals(2, rootNode.path("experimental").path("subagent_depth").asInt());

        // Check agents
        JsonNode agentsNode = rootNode.path("agent");
        assertEquals("primary", agentsNode.path("boss").path("mode").asText());
        assertEquals("subagent", agentsNode.path("lead").path("mode").asText());
        assertEquals("subagent", agentsNode.path("worker").path("mode").asText());

        assertEquals("I delegate work", agentsNode.path("boss").path("prompt").asText());
        assertEquals(15, agentsNode.path("boss").path("steps").asInt());

        // Permissions on boss
        JsonNode perms = agentsNode.path("boss").path("permissions");
        assertEquals(2, perms.size());
        assertEquals("*", perms.get(0).path("action").asText());
        assertEquals("deny", perms.get(0).path("effect").asText());
        assertEquals("skill", perms.get(1).path("action").asText());
        assertEquals("allow", perms.get(1).path("effect").asText());

        // MCP servers in v2-native format: mcp.servers.<name>
        JsonNode mcpServers = rootNode.path("mcp").path("servers");
        assertEquals("remote", mcpServers.path("jvm_tools").path("type").asText());
        assertEquals("http://127.0.0.1:54321/mcp", mcpServers.path("jvm_tools").path("url").asText());
        assertEquals("Bearer tok-test", mcpServers.path("jvm_tools").path("headers").path("Authorization").asText());

        // Check skill file was written
        Path skillFile = canonicalDir.resolve(".opencode").resolve("skills").resolve("launch-protocol").resolve("SKILL.md");
        assertTrue(Files.exists(skillFile));
        String skillMarkdown = Files.readString(skillFile);
        assertTrue(skillMarkdown.contains("name: launch-protocol"));
        assertTrue(skillMarkdown.contains("Secret launch code"));
        assertTrue(skillMarkdown.contains("# Launch Code: 42"));
    }
}
