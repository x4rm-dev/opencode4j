package io.opencode4j.model.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.opencode4j.model.Immutable;
import io.opencode4j.model.mcp.McpServerConfig;
import io.opencode4j.model.mcp.McpTimeout;
import java.util.Map;

/** MCP settings of a configuration file: default timeouts and the servers it declares. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ConfigMcp(
        McpTimeout timeout,
        Map<String, McpServerConfig> servers
) {

    public ConfigMcp {
        servers = Immutable.map(servers);
    }
}
