package io.opencode4j.model.integration;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** A method whose {@code type} this client version does not recognise. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record UnknownMethod(
        String type
) implements IntegrationMethod {
}
