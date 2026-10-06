package io.opencode4j.model.mcp;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * A configured MCP server and its connection state.
 *
 * @param integrationID the integration this server is linked to, or {@code null}
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record McpServer(
        String name,
        McpStatus status,
        String integrationID
) {
}
