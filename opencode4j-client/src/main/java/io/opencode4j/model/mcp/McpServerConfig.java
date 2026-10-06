package io.opencode4j.model.mcp;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import java.util.List;

/**
 * Configuration of an MCP server, either a local process or a remote endpoint.
 *
 * <p>Unrecognised {@code type} values deserialize to {@link UnknownMcpServerConfig}.</p>
 */
@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.PROPERTY,
        property = "type",
        visible = true,
        defaultImpl = UnknownMcpServerConfig.class)
@JsonSubTypes({
        @JsonSubTypes.Type(value = McpLocalServerConfig.class, name = "local"),
        @JsonSubTypes.Type(value = McpRemoteServerConfig.class, name = "remote")
})
public sealed interface McpServerConfig
        permits McpLocalServerConfig, McpRemoteServerConfig, UnknownMcpServerConfig {

    /** A local server started with {@code command}. */
    static McpLocalServerConfig local(List<String> command) {
        return new McpLocalServerConfig(command, null, null, null, null, null, null);
    }

    /** A remote server reached at {@code url}. */
    static McpRemoteServerConfig remote(String url) {
        return new McpRemoteServerConfig(url, null, null, null, null, null, null);
    }
}
