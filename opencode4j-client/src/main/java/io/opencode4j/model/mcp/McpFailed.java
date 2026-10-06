package io.opencode4j.model.mcp;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** The server failed to start or connect; {@code error} explains why. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record McpFailed(
        String error
) implements McpStatus {
}
