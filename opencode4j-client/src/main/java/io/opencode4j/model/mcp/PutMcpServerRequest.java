package io.opencode4j.model.mcp;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Objects;

/** Request body that adds or replaces an MCP server. */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public record PutMcpServerRequest(
        McpServerConfig config
) {

    public PutMcpServerRequest {
        Objects.requireNonNull(config, "config");
    }
}
