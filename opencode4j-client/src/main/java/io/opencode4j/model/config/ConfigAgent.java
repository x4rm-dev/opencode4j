package io.opencode4j.model.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.opencode4j.model.Immutable;
import io.opencode4j.model.PermissionRule;
import java.util.List;

/** An agent defined in a configuration file. Settings not listed here are ignored. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ConfigAgent(
        ConfigModel model,
        String system,
        String description,
        AgentMode mode,
        Boolean hidden,
        String color,
        Integer steps,
        Boolean disabled,
        List<PermissionRule> permissions
) {

    public ConfigAgent {
        permissions = Immutable.list(permissions);
    }
}
