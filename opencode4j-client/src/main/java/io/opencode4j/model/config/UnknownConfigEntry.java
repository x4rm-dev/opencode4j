package io.opencode4j.model.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** A configuration entry whose {@code type} this client version does not recognise. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record UnknownConfigEntry(
        String type
) implements ConfigEntry {
}
