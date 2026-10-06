package io.opencode4j.model.mcp;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.opencode4j.model.Immutable;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** A local MCP server: a process OpenCode starts and talks to over standard I/O. */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@JsonIgnoreProperties(ignoreUnknown = true)
public record McpLocalServerConfig(
        List<String> command,
        String cwd,
        Map<String, String> environment,
        Boolean disabled,
        Boolean codemode,
        McpTimeout timeout,
        McpProtocol protocol
) implements McpServerConfig {

    public McpLocalServerConfig {
        Objects.requireNonNull(command, "command");
        command = Immutable.list(command);
        environment = Immutable.map(environment);
    }
}
