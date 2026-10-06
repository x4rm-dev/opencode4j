package io.opencode4j.model.integration;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** A connection whose {@code type} this client version does not recognise. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record UnknownConnection(
        String type
) implements Connection {
}
