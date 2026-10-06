package io.opencode4j.model.mcp;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** A resource exposed by an MCP server. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record McpResource(
        String server,
        String name,
        String uri,
        String description,
        String mimeType
) {
}
