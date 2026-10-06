package io.opencode4j.model.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** A settings document. {@code path} is the file it was read from, or {@code null} for built-in settings. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ConfigDocument(
        String path,
        ConfigInfo info
) implements ConfigEntry {
}
