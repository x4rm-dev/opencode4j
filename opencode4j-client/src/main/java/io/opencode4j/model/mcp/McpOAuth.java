package io.opencode4j.model.mcp;

/**
 * OAuth setting of a remote MCP server: either a configuration ({@link McpOAuthConfig}) or the literal
 * {@code false} that turns OAuth off ({@link McpOAuthDisabled}).
 */
public sealed interface McpOAuth permits McpOAuthConfig, McpOAuthDisabled {
}
