package io.opencode4j.model.mcp;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** A status this client version does not recognise; {@code status} is the raw wire value. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record UnknownMcpStatus(
        String status
) implements McpStatus {
}
