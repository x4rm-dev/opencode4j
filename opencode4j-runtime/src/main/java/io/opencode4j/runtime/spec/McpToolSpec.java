package io.opencode4j.runtime.spec;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Declares an external MCP server (local stdio command or remote HTTP endpoint) available to the agent.
 *
 * @param name        unique name of the MCP server
 * @param description brief description of the server capability
 * @param type        "local" or "remote"
 * @param command     command tokens for local process, or null if remote
 * @param url         endpoint URL for remote server, or null if local
 * @param environment environment variables passed to local process
 * @param headers     HTTP headers sent to remote endpoint
 */
public record McpToolSpec(
        String name,
        String description,
        String type,
        List<String> command,
        String url,
        Map<String, String> environment,
        Map<String, String> headers
) implements ToolSpec {

    public McpToolSpec {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(type, "type");
        if (!"local".equals(type) && !"remote".equals(type)) {
            throw new IllegalArgumentException("type must be 'local' or 'remote': " + type);
        }
        if ("local".equals(type) && (command == null || command.isEmpty())) {
            throw new IllegalArgumentException("command must not be empty for local MCP server");
        }
        if ("remote".equals(type) && (url == null || url.isBlank())) {
            throw new IllegalArgumentException("url must not be empty for remote MCP server");
        }
        command = command == null ? null : List.copyOf(command);
        environment = environment == null ? Map.of() : Map.copyOf(environment);
        headers = headers == null ? Map.of() : Map.copyOf(headers);
    }

    public static McpToolSpec local(String name, String description, List<String> command) {
        return new McpToolSpec(name, description, "local", command, null, Map.of(), Map.of());
    }

    public static McpToolSpec local(String name, String description, List<String> command, Map<String, String> env) {
        return new McpToolSpec(name, description, "local", command, null, env, Map.of());
    }

    public static McpToolSpec remote(String name, String description, String url) {
        return new McpToolSpec(name, description, "remote", null, url, Map.of(), Map.of());
    }

    public static McpToolSpec remote(String name, String description, String url, Map<String, String> headers) {
        return new McpToolSpec(name, description, "remote", null, url, Map.of(), headers);
    }
}
