package io.opencode4j.runtime.spec;

import io.opencode4j.runtime.tool.DynamicToolProvider;
import java.util.Objects;

/**
 * Exposes a {@link DynamicToolProvider} to the agent through the in-JVM MCP server.
 *
 * @param name        logical name of this tool group
 * @param description description of what the tools provide
 * @param provider    the provider that lists and executes the tools
 */
public record DynamicToolSpec(
        String name,
        String description,
        DynamicToolProvider provider
) implements ToolSpec {

    public DynamicToolSpec {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(description, "description");
        Objects.requireNonNull(provider, "provider");
    }

    public static DynamicToolSpec of(String name, String description, DynamicToolProvider provider) {
        return new DynamicToolSpec(name, description, provider);
    }
}
