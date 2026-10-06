package io.opencode4j.model.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.opencode4j.model.PermissionEffect;
import io.opencode4j.model.mcp.McpLocalServerConfig;
import java.util.List;
import org.junit.jupiter.api.Test;

class ConfigModelsTest {

    /** The shape of OpenCode 2.0.20's {@code GET /api/config}, with paths anonymised. */
    private static final String LIVE = """
            [
              {"type":"document","path":"/home/u/.config/opencode/opencode.json","info":{
                "$schema":"https://opencode.ai/config.json",
                "mcp":{"servers":{"codebase-memory-mcp":{"type":"local","command":["/bin/cmm"],"disabled":false}}},
                "plugins":[]}},
              {"type":"directory","path":"/home/u/.config/opencode"},
              {"type":"directory","path":"/home/u/.opencode"}
            ]""";

    private static final String RICH = """
            {"shell":"/bin/zsh","model":"github-copilot/gpt-6.1-sol","default_agent":"build",
             "update":"notify","share":"disabled","enterprise":{"url":"https://ghe.test"},
             "username":"alice","snapshots":true,"skills":["a"],"instructions":["AGENTS.md"],
             "permissions":[{"action":"bash","resource":"*","effect":"ask"}],
             "agents":{"review":{"model":{"providerID":"anthropic","model":"claude","variant":"high"},
                      "mode":"subagent","steps":5,"hidden":true,
                      "permissions":[{"action":"edit","resource":"*","effect":"deny"}]}},
             "commands":{"ship":{"template":"deploy","model":"p/m","subtask":true}},
             "mcp":{"timeout":{"startup":2000},"servers":{}},
             "providers":{"x":{"models":{}}},"formatter":false}""";

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void readsTheEntriesOfALiveConfigResponse() throws Exception {
        // When
        List<ConfigEntry> entries = mapper.readValue(LIVE, new TypeReference<>() { });

        // Then
        assertEquals(3, entries.size());
        ConfigDocument document = assertInstanceOf(ConfigDocument.class, entries.get(0));
        assertEquals("/home/u/.config/opencode/opencode.json", document.path());
        assertEquals("https://opencode.ai/config.json", document.info().schema());
        McpLocalServerConfig server = assertInstanceOf(
                McpLocalServerConfig.class, document.info().mcp().servers().get("codebase-memory-mcp"));
        assertEquals(List.of("/bin/cmm"), server.command());
        assertEquals("/home/u/.config/opencode", assertInstanceOf(ConfigDirectory.class, entries.get(1)).path());
    }

    @Test
    void readsTheCommonSettings() throws Exception {
        // When
        ConfigInfo info = mapper.readValue(RICH, ConfigInfo.class);

        // Then
        assertEquals("/bin/zsh", info.shell());
        assertEquals(new ConfigModel("github-copilot", "gpt-6.1-sol", null), info.model());
        assertEquals("build", info.defaultAgent());
        assertEquals(ConfigUpdate.NOTIFY, info.update());
        assertEquals(ConfigShare.DISABLED, info.share());
        assertEquals("https://ghe.test", info.enterprise().url());
        assertEquals(true, info.snapshots());
        assertEquals(List.of("AGENTS.md"), info.instructions());
        assertEquals(PermissionEffect.ASK, info.permissions().getFirst().effect());
        ConfigAgent review = info.agents().get("review");
        assertEquals(new ConfigModel("anthropic", "claude", "high"), review.model());
        assertEquals(AgentMode.SUBAGENT, review.mode());
        assertEquals(PermissionEffect.DENY, review.permissions().getFirst().effect());
        assertEquals(new ConfigModel("p", "m", null), info.commands().get("ship").model());
        assertEquals(2000, info.mcp().timeout().startup());
    }

    @Test
    void ignoresSettingsItDoesNotModelInsteadOfFailing() throws Exception {
        // RICH contains providers and a boolean formatter, neither of which ConfigInfo models
        assertEquals("/bin/zsh", mapper.readValue(RICH, ConfigInfo.class).shell());
    }

    @Test
    void anEmptyDocumentYieldsEmptyCollectionsNotNulls() throws Exception {
        // When
        ConfigInfo info = mapper.readValue("{}", ConfigInfo.class);

        // Then
        assertTrue(info.skills().isEmpty());
        assertTrue(info.agents().isEmpty());
        assertTrue(info.permissions().isEmpty());
        assertNull(info.model());
    }

    @Test
    void anUnknownEntryTypeBecomesUnknown() throws Exception {
        assertEquals("remote", assertInstanceOf(UnknownConfigEntry.class,
                mapper.readValue("{\"type\":\"remote\"}", ConfigEntry.class)).type());
    }

    @Test
    void parsesModelStringsAndRejectsMalformedOnes() {
        assertEquals(new ConfigModel("openrouter", "a/b", null), ConfigModel.parse("openrouter/a/b"));
        assertThrows(IllegalArgumentException.class, () -> ConfigModel.parse("nomodel"));
        assertThrows(JsonProcessingException.class, () -> mapper.readValue("42", ConfigModel.class));
    }

    @Test
    void writesAModelBackAsAnObject() throws Exception {
        assertEquals(
                "{\"providerID\":\"p\",\"model\":\"m\"}",
                mapper.writeValueAsString(ConfigModel.parse("p/m")));
    }

    @Test
    void readsShellOptions() throws Exception {
        // Given: copied from a live response
        String json = "[{\"path\":\"/bin/bash\",\"name\":\"bash\",\"acceptable\":true},"
                + "{\"path\":\"/bin/csh\",\"name\":\"csh\",\"acceptable\":false}]";

        // When
        List<ShellOption> shells = mapper.readValue(json, new TypeReference<>() { });

        // Then
        assertEquals("/bin/bash", shells.get(0).path());
        assertEquals(false, shells.get(1).acceptable());
    }

    @Test
    void alwaysSendsTheShellEvenWhenClearingIt() throws Exception {
        assertEquals("{\"shell\":\"/bin/zsh\"}", mapper.writeValueAsString(new ConfigPatch("/bin/zsh")));
        assertEquals("{\"shell\":null}", mapper.writeValueAsString(new ConfigPatch(null)));
    }

    @Test
    void mapsFutureEnumValuesToUnknown() throws Exception {
        assertEquals(ConfigUpdate.UNKNOWN, mapper.readValue("\"weekly\"", ConfigUpdate.class));
        assertEquals(ConfigShare.UNKNOWN, mapper.readValue("\"teams\"", ConfigShare.class));
        assertEquals(AgentMode.UNKNOWN, mapper.readValue("\"background\"", AgentMode.class));
        assertEquals(PermissionEffect.UNKNOWN, mapper.readValue("\"audit\"", PermissionEffect.class));
    }
}
