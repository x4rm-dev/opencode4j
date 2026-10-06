package io.opencode4j.model.mcp;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** An MCP server configuration whose {@code type} this client version does not recognise. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record UnknownMcpServerConfig(
        String type
) implements McpServerConfig {
}
