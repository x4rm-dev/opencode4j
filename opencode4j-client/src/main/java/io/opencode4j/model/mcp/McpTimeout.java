package io.opencode4j.model.mcp;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

/** Timeouts, in milliseconds, for talking to an MCP server. Unset values use the server's defaults. */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public record McpTimeout(
        Integer startup,
        Integer catalog,
        Integer execution
) {
}
