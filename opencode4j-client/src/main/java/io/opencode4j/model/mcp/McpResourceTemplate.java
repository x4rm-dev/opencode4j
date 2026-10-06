package io.opencode4j.model.mcp;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** A parameterised resource an MCP server can produce, addressed by an RFC 6570 URI template. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record McpResourceTemplate(
        String server,
        String name,
        String uriTemplate,
        String description,
        String mimeType
) {
}
