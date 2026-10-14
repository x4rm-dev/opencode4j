package io.opencode4j.runtime.tool;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Supplies tools whose names and JSON schemas are only known at runtime (for example, tools discovered
 * from another MCP server), as opposed to reflective {@link Tool}-annotated methods.
 *
 * <p>Implementations are hosted by the in-JVM MCP server. {@link #listTools()} is consulted on every
 * {@code tools/list} request, so a provider may change the set it offers between agent turns.</p>
 */
public interface DynamicToolProvider {

    /** Tools currently offered by this provider. */
    List<ToolDefinition> listTools();

    /**
     * Invokes one tool.
     *
     * @param toolName  a name returned by {@link #listTools()}
     * @param arguments the decoded JSON arguments (never {@code null})
     * @param context   the calling run/session context
     * @return a {@link io.opencode4j.runtime.media.MediaResult} for image/document results, otherwise any
     *         object whose {@code String.valueOf} is returned to the model
     * @throws Exception any failure is reported to the model as a tool error
     */
    Object call(String toolName, Map<String, Object> arguments, ToolContext context) throws Exception;

    /**
     * A tool advertised by a {@link DynamicToolProvider}.
     *
     * @param name        tool name, unique across all tools of the session
     * @param description what the tool does
     * @param inputSchema JSON schema of the arguments object ({@code type: object}); may be {@code null} for none
     */
    record ToolDefinition(String name, String description, Map<String, Object> inputSchema) {

        public ToolDefinition {
            Objects.requireNonNull(name, "name");
            description = description == null ? "" : description;
            inputSchema = inputSchema == null ? Map.of("type", "object", "properties", Map.of()) : inputSchema;
        }
    }
}
