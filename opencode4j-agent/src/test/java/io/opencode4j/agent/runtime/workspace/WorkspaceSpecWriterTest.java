package io.opencode4j.agent.runtime.workspace;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.opencode4j.runtime.spec.AgentSpec;
import io.opencode4j.runtime.spec.McpToolSpec;
import io.opencode4j.runtime.spec.PermissionRule;
import io.opencode4j.runtime.spec.PolicyRule;
import io.opencode4j.runtime.spec.WorkspaceSpec;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WorkspaceSpecWriterTest {

    private final WorkspaceWriter writer = new WorkspaceWriter();
    private final ObjectMapper mapper = new ObjectMapper();

    private static WorkspaceSpec.Builder debugging() {
        AgentSpec root = AgentSpec.builder("sre-debugger")
                .instruction("{file:.opencode/prompts/sre-debugger.md}")
                .permission(PermissionRule.allow("victorialogs_*", "*"))
                .subAgent(AgentSpec.builder("logs-analyst").build())
                .build();
        return WorkspaceSpec.builder("debugging/rel_0042", root)
                .model("godric/global:LLM_ROOT")
                .smallModel("godric/global:LLM_SMALL")
                .instruction("timezone.md", "Use IST.")
                .file(".opencode/prompts/sre-debugger.md", "You debug production.")
                .mcpServer(McpToolSpec.remote("victorialogs", "logs", "{env:VL_MCP_URL}",
                        Map.of("Authorization", "{env:VL_MCP_AUTH}")))
                .policy(PolicyRule.denyTool("shell"))
                .enabledProvider("godric")
                .autoCompaction(true);
    }

    @Test
    void rendersWorkspaceWideSettingsWithDenyAllByDefault(@TempDir Path tempDir) throws Exception {
        // When
        WorkspaceWriter.Written written = writer.write(tempDir.resolve("ws"), writer.render(debugging().build(), Map.of()));

        // Then
        JsonNode config = mapper.readTree(written.directory().resolve("opencode.json").toFile());
        assertEquals("https://opencode.ai/config.json", config.path("$schema").asText());
        assertTrue(config.path("permission").isMissingNode(), "workspace rules lead each agent's rules instead");
        assertEquals("godric/global:LLM_ROOT", config.path("model").asText());
        assertEquals("godric/global:LLM_SMALL", config.path("small_model").asText());
        assertEquals(".opencode/instructions/timezone.md", config.path("instructions").get(0).asText());
        assertEquals("tool.use", config.path("policies").get(0).path("action").asText());
        assertEquals("shell:*", config.path("policies").get(0).path("resource").asText());
        assertEquals("deny", config.path("policies").get(0).path("effect").asText());
        assertEquals("godric", config.path("enabled_providers").get(0).asText());
        assertEquals("disabled", config.path("share").asText());
        assertFalse(config.path("autoupdate").asBoolean(true));
        assertTrue(config.path("compaction").path("auto").asBoolean());
        assertEquals(0, config.path("plugin").size(), "no plugins unless asked for");
        assertEquals("{env:VL_MCP_AUTH}", config.path("mcp").path("servers").path("victorialogs")
                .path("headers").path("Authorization").asText());
        JsonNode rootRules = config.path("agent").path("sre-debugger").path("permission");
        assertEquals(List.of("*", "victorialogs_*"), iterableNames(rootRules));
        assertEquals("deny", rootRules.path("*").asText());
        assertEquals("allow", rootRules.path("victorialogs_*").asText());
        assertEquals("deny", config.path("agent").path("logs-analyst").path("permission").path("*").asText());
        assertEquals("Use IST.", Files.readString(written.directory().resolve(".opencode/instructions/timezone.md")));
        assertEquals("You debug production.",
                Files.readString(written.directory().resolve(".opencode/prompts/sre-debugger.md")));
    }

    @Test
    void writingTheSameContentAgainChangesNothing(@TempDir Path tempDir) throws Exception {
        // Given
        Path directory = tempDir.resolve("ws");
        WorkspaceWriter.Written first = writer.write(directory, writer.render(debugging().build(), Map.of()));
        long modified = Files.getLastModifiedTime(directory.resolve("opencode.json")).toMillis();

        // When
        WorkspaceWriter.Written second = writer.write(directory, writer.render(debugging().build(), Map.of()));
        long modifiedAfterRewrite = Files.getLastModifiedTime(directory.resolve("opencode.json")).toMillis();
        WorkspaceWriter.Written changed = writer.write(directory,
                writer.render(debugging().smallModel("godric/other").build(), Map.of()));

        // Then
        assertTrue(first.changed());
        assertFalse(second.changed());
        assertEquals(first.hash(), second.hash());
        assertEquals(modified, modifiedAfterRewrite);
        assertTrue(changed.changed());
        assertNotEquals(first.hash(), changed.hash());
        assertEquals(changed.hash(), Files.readString(directory.resolve(".opencode4j/hash")));
    }

    @Test
    void restrictsFilesToTheOwner(@TempDir Path tempDir) throws Exception {
        if (!FileSystems.getDefault().supportedFileAttributeViews().contains("posix")) {
            return;
        }
        // When
        Path directory = writer.write(tempDir.resolve("a/b"), writer.render(debugging().build(), Map.of())).directory();

        // Then
        assertEquals("rw-------", PosixFilePermissions.toString(
                Files.getPosixFilePermissions(directory.resolve("opencode.json"))));
        assertEquals("rwx------", PosixFilePermissions.toString(Files.getPosixFilePermissions(directory)));
        assertEquals("rwx------", PosixFilePermissions.toString(
                Files.getPosixFilePermissions(directory.resolve(".opencode/instructions"))));
    }

    @Test
    void explicitWorkspacePermissionsReplaceTheDefault(@TempDir Path tempDir) throws Exception {
        // When
        Path directory = writer.write(tempDir.resolve("ws"), writer.render(debugging()
                .permissions(List.of(PermissionRule.deny("*", "*"), PermissionRule.allow("skill", "*")))
                .build(), Map.of())).directory();

        // Then
        JsonNode permission = mapper.readTree(directory.resolve("opencode.json").toFile())
                .path("agent").path("logs-analyst").path("permission");
        assertEquals("deny", permission.path("*").asText());
        assertEquals("allow", permission.path("skill").asText());
    }

    private static List<String> iterableNames(JsonNode node) {
        List<String> names = new java.util.ArrayList<>();
        node.fieldNames().forEachRemaining(names::add);
        return names;
    }

    @Test
    void rejectsIdsAndPathsThatEscapeTheWorkspaceRoot() {
        AgentSpec root = AgentSpec.builder("a").build();
        assertThrows(IllegalArgumentException.class, () -> WorkspaceSpec.builder("../etc", root).build());
        assertThrows(IllegalArgumentException.class, () -> WorkspaceSpec.builder("/abs", root).build());
        assertThrows(IllegalArgumentException.class, () -> WorkspaceSpec.builder("ok", root)
                .file("../outside", "x").build());
        assertThrows(IllegalArgumentException.class, () -> WorkspaceSpec.builder("ok", root)
                .instruction("nested/file.md", "x").build());
    }
}
