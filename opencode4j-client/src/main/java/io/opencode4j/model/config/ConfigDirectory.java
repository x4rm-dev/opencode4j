package io.opencode4j.model.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** A directory the server scans for configuration such as agents and commands. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ConfigDirectory(
        String path
) implements ConfigEntry {
}
