package io.opencode4j.client.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.opencode4j.client.HttpMethod;
import io.opencode4j.model.mcp.McpConnected;
import io.opencode4j.model.mcp.McpFailed;
import io.opencode4j.model.mcp.McpResourceCatalog;
import io.opencode4j.model.mcp.McpServer;
import io.opencode4j.model.mcp.McpServerConfig;
import java.util.List;
import org.junit.jupiter.api.Test;

class McpApiTest extends MockServerTest {

    private static final String LOCATION = "location%5Bdirectory%5D=%2Fwork";

    @Test
    void listsServersWithTheirStatus() throws Exception {
        // Given
        server.enqueue(json("{\"location\":{\"directory\":\"/work\"},\"data\":["
                + "{\"name\":\"a\",\"status\":{\"status\":\"connected\"}},"
                + "{\"name\":\"b\",\"status\":{\"status\":\"failed\",\"error\":\"boom\"}}]}"));

        // When
        List<McpServer> servers = client.mcp().list("/work");

        // Then
        assertInstanceOf(McpConnected.class, servers.get(0).status());
        assertEquals("boom", assertInstanceOf(McpFailed.class, servers.get(1).status()).error());
        assertRequest(HttpMethod.GET, "/api/mcp?" + LOCATION, "");
    }

    @Test
    void listsResources() throws Exception {
        // Given
        server.enqueue(json("{\"data\":{\"resources\":[{\"server\":\"s\",\"name\":\"n\",\"uri\":\"u\"}],"
                + "\"templates\":[]}}"));

        // When
        McpResourceCatalog catalog = client.mcp().resources(null);

        // Then
        assertEquals("u", catalog.resources().getFirst().uri());
        assertRequest(HttpMethod.GET, "/api/mcp/resource", "");
    }

    @Test
    void putsAServerWrappingItsConfig() throws Exception {
        // Given
        server.enqueue(noContent());

        // When
        client.mcp().put("my server", McpServerConfig.local(List.of("npx", "srv")), "/work");

        // Then
        assertRequest(
                HttpMethod.PUT,
                "/api/experimental/mcp/my%20server?" + LOCATION,
                "{\"config\":{\"type\":\"local\",\"command\":[\"npx\",\"srv\"]}}");
    }

    @Test
    void removesConnectsAndDisconnects() throws Exception {
        // Given
        server.enqueue(noContent());
        server.enqueue(noContent());
        server.enqueue(noContent());

        // When
        client.mcp().remove("a", null);
        client.mcp().connect("a", null);
        client.mcp().disconnect("a", "/work");

        // Then
        assertRequest(HttpMethod.DELETE, "/api/experimental/mcp/a", "");
        assertRequest(HttpMethod.POST, "/api/experimental/mcp/a/connect", "");
        assertRequest(HttpMethod.POST, "/api/experimental/mcp/a/disconnect?" + LOCATION, "");
    }

    @Test
    void rejectsMissingArgumentsBeforeSendingAnything() {
        // Then
        assertThrows(NullPointerException.class, () -> client.mcp().remove(null, null));
        assertThrows(NullPointerException.class, () -> client.mcp().put("a", null, null));
        assertEquals(0, server.getRequestCount());
    }
}
