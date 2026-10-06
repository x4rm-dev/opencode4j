package io.opencode4j.model.mcp;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** The server needs authentication before it can be used. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record McpNeedsAuth(
        String error
) implements McpStatus {
}
