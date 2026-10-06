package io.opencode4j.model.integration;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** A connection backed by an environment variable on the server. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record EnvConnection(
        String name
) implements Connection {
}
