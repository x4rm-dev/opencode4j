package io.opencode4j.model.mcp;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** The server is turned off in the configuration. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record McpDisabled() implements McpStatus {
}
