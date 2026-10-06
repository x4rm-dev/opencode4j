package io.opencode4j.model.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.opencode4j.model.Immutable;
import io.opencode4j.model.PermissionRule;
import java.util.List;
import java.util.Map;

/**
 * The settings of one configuration document.
 *
 * <p>This exposes the commonly used settings. Others the server may send (providers and models, formatters,
 * LSP servers, plugins, references, compaction and so on) are ignored rather than modelled; in the v2 API
 * providers are connected through {@code IntegrationApi}, not configured through this API.</p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ConfigInfo(
        @JsonProperty("$schema") String schema,
        String shell,
        ConfigModel model,
        @JsonProperty("default_agent") String defaultAgent,
        ConfigUpdate update,
        ConfigShare share,
        ConfigEnterprise enterprise,
        String username,
        Boolean snapshots,
        List<String> skills,
        List<String> instructions,
        List<PermissionRule> permissions,
        Map<String, ConfigAgent> agents,
        Map<String, ConfigCommand> commands,
        ConfigMcp mcp
) {

    public ConfigInfo {
        skills = Immutable.list(skills);
        instructions = Immutable.list(instructions);
        permissions = Immutable.list(permissions);
        agents = Immutable.map(agents);
        commands = Immutable.map(commands);
    }
}
