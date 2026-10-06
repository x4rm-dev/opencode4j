package io.opencode4j.model.mcp;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** The server is still connecting. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record McpPending() implements McpStatus {
}
