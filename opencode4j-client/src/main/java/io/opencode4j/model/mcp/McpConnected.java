package io.opencode4j.model.mcp;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** The server is connected and usable. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record McpConnected() implements McpStatus {
}
