package io.opencode4j.event;

/** An MCP server's connection status settled (connected, failed, needs auth, closed). Read the new status through {@code McpApi}. */
public record McpStatusChanged(
        String server
) implements OpenCodeEvent {

    @Override
    public String type() {
        return "mcp.status.changed";
    }
}
