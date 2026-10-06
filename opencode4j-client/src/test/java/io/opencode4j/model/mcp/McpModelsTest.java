package io.opencode4j.model.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class McpModelsTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void deserializesEveryServerStatus() throws Exception {
        // Given
        String json = "[{\"name\":\"a\",\"status\":{\"status\":\"connected\"}},"
                + "{\"name\":\"b\",\"status\":{\"status\":\"pending\"}},"
                + "{\"name\":\"c\",\"status\":{\"status\":\"disabled\"},\"integrationID\":\"int_1\"},"
                + "{\"name\":\"d\",\"status\":{\"status\":\"failed\",\"error\":\"spawn ENOENT\"}},"
                + "{\"name\":\"e\",\"status\":{\"status\":\"needs_auth\",\"error\":\"401\"}}]";

        // When
        List<McpServer> servers = mapper.readValue(json, new TypeReference<>() { });

        // Then
        assertInstanceOf(McpConnected.class, servers.get(0).status());
        assertInstanceOf(McpPending.class, servers.get(1).status());
        assertInstanceOf(McpDisabled.class, servers.get(2).status());
        assertEquals("int_1", servers.get(2).integrationID());
        assertEquals("spawn ENOENT", assertInstanceOf(McpFailed.class, servers.get(3).status()).error());
        assertEquals("401", assertInstanceOf(McpNeedsAuth.class, servers.get(4).status()).error());
    }

    @Test
    void mapsAFutureStatusToUnknownInsteadOfFailing() throws Exception {
        // When
        McpServer server = mapper.readValue(
                "{\"name\":\"x\",\"status\":{\"status\":\"quarantined\"}}", McpServer.class);

        // Then
        assertEquals("quarantined", assertInstanceOf(UnknownMcpStatus.class, server.status()).status());
    }

    @Test
    void deserializesTheResourceCatalog() throws Exception {
        // Given
        String json = "{\"resources\":[{\"server\":\"s\",\"name\":\"n\",\"uri\":\"file:///a\","
                + "\"mimeType\":\"text/plain\"}],\"templates\":[{\"server\":\"s\",\"name\":\"t\","
                + "\"uriTemplate\":\"file:///{path}\"}]}";

        // When
        McpResourceCatalog catalog = mapper.readValue(json, McpResourceCatalog.class);

        // Then
        assertEquals("file:///a", catalog.resources().getFirst().uri());
        assertEquals("text/plain", catalog.resources().getFirst().mimeType());
        assertEquals("file:///{path}", catalog.templates().getFirst().uriTemplate());
        assertEquals(0, mapper.readValue("{}", McpResourceCatalog.class).resources().size());
    }

    @Test
    void serializesALocalServerAsTheWireShapeOmittingEmptyFields() throws Exception {
        // Given
        McpLocalServerConfig config = new McpLocalServerConfig(
                List.of("npx", "-y", "server"), "/work", Map.of("TOKEN", "x"), false, null,
                new McpTimeout(1000, null, 5000), McpProtocol.V2026_07_28);

        // Then
        assertEquals(
                "{\"type\":\"local\",\"command\":[\"npx\",\"-y\",\"server\"],\"cwd\":\"/work\","
                        + "\"environment\":{\"TOKEN\":\"x\"},\"disabled\":false,"
                        + "\"timeout\":{\"startup\":1000,\"execution\":5000},\"protocol\":\"2026-07-28\"}",
                mapper.writeValueAsString(config));
        assertEquals(
                "{\"type\":\"local\",\"command\":[\"server\"]}",
                mapper.writeValueAsString(McpServerConfig.local(List.of("server"))));
    }

    @Test
    void serializesARemoteServerWithOAuthAsAnObjectOrAsFalse() throws Exception {
        // Given
        McpRemoteServerConfig withOAuth = new McpRemoteServerConfig(
                "https://mcp.test", Map.of("X-Key", "k"),
                new McpOAuthConfig("id", null, "read", 8080, null, null), null, true, null, McpProtocol.AUTO);
        McpRemoteServerConfig withoutOAuth = new McpRemoteServerConfig(
                "https://mcp.test", null, new McpOAuthDisabled(), null, null, null, null);

        // Then
        assertEquals(
                "{\"type\":\"remote\",\"url\":\"https://mcp.test\",\"headers\":{\"X-Key\":\"k\"},"
                        + "\"oauth\":{\"client_id\":\"id\",\"scope\":\"read\",\"callback_port\":8080},"
                        + "\"codemode\":true,\"protocol\":\"auto\"}",
                mapper.writeValueAsString(withOAuth));
        assertEquals(
                "{\"type\":\"remote\",\"url\":\"https://mcp.test\",\"oauth\":false}",
                mapper.writeValueAsString(withoutOAuth));
    }

    @Test
    void readsTheOAuthSettingInBothForms() throws Exception {
        // When
        McpRemoteServerConfig disabled = assertInstanceOf(McpRemoteServerConfig.class, mapper.readValue(
                "{\"type\":\"remote\",\"url\":\"u\",\"oauth\":false}", McpServerConfig.class));
        McpRemoteServerConfig configured = assertInstanceOf(McpRemoteServerConfig.class, mapper.readValue(
                "{\"type\":\"remote\",\"url\":\"u\",\"oauth\":{\"client_id\":\"c\",\"callback_port\":1}}",
                McpServerConfig.class));
        McpRemoteServerConfig absent = assertInstanceOf(McpRemoteServerConfig.class, mapper.readValue(
                "{\"type\":\"remote\",\"url\":\"u\"}", McpServerConfig.class));

        // Then
        assertInstanceOf(McpOAuthDisabled.class, disabled.oauth());
        assertEquals("c", assertInstanceOf(McpOAuthConfig.class, configured.oauth()).clientId());
        assertNull(absent.oauth());
        assertThrows(JsonProcessingException.class, () -> mapper.readValue(
                "{\"type\":\"remote\",\"url\":\"u\",\"oauth\":true}", McpServerConfig.class));
    }

    @Test
    void readsTheServerShapeSeenInALiveConfigFile() throws Exception {
        // Given: a local server entry exactly as OpenCode 2.0.20 returned it
        String json = "{\"type\":\"local\",\"command\":[\"/Users/x/.local/bin/codebase-memory-mcp\"],"
                + "\"disabled\":false}";

        // When
        McpLocalServerConfig config = assertInstanceOf(
                McpLocalServerConfig.class, mapper.readValue(json, McpServerConfig.class));

        // Then
        assertEquals(List.of("/Users/x/.local/bin/codebase-memory-mcp"), config.command());
        assertEquals(false, config.disabled());
        assertEquals(Map.of(), config.environment());
    }

    @Test
    void anUnknownServerTypeBecomesUnknown() throws Exception {
        assertEquals("sse", assertInstanceOf(UnknownMcpServerConfig.class,
                mapper.readValue("{\"type\":\"sse\"}", McpServerConfig.class)).type());
    }

    @Test
    void wrapsAServerConfigForPut() throws Exception {
        assertEquals(
                "{\"config\":{\"type\":\"remote\",\"url\":\"https://x\"}}",
                mapper.writeValueAsString(new PutMcpServerRequest(McpServerConfig.remote("https://x"))));
    }

    @Test
    void requiresTheFieldsTheServerRequires() {
        assertThrows(NullPointerException.class, () -> McpServerConfig.local(null));
        assertThrows(NullPointerException.class, () -> McpServerConfig.remote(null));
        assertThrows(NullPointerException.class, () -> new PutMcpServerRequest(null));
    }

    @Test
    void mapsFutureProtocolValuesToUnknown() throws Exception {
        assertEquals(McpProtocol.UNKNOWN, mapper.readValue("\"2030-01-01\"", McpProtocol.class));
        assertEquals(McpProtocol.V2026_07_28, mapper.readValue("\"2026-07-28\"", McpProtocol.class));
    }
}
