package io.opencode4j.model.mcp;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * The connection state of an MCP server. Unrecognised states deserialize to {@link UnknownMcpStatus}.
 */
@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.PROPERTY,
        property = "status",
        visible = true,
        defaultImpl = UnknownMcpStatus.class)
@JsonSubTypes({
        @JsonSubTypes.Type(value = McpConnected.class, name = "connected"),
        @JsonSubTypes.Type(value = McpPending.class, name = "pending"),
        @JsonSubTypes.Type(value = McpDisabled.class, name = "disabled"),
        @JsonSubTypes.Type(value = McpFailed.class, name = "failed"),
        @JsonSubTypes.Type(value = McpNeedsAuth.class, name = "needs_auth")
})
public sealed interface McpStatus
        permits McpConnected, McpPending, McpDisabled, McpFailed, McpNeedsAuth, UnknownMcpStatus {
}
