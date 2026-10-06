package io.opencode4j.model.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** A slash command defined in a configuration file. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ConfigCommand(
        String template,
        String description,
        String agent,
        ConfigModel model,
        Boolean subagent,
        Boolean subtask
) {
}
