package io.opencode4j.model.integration;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** A connection backed by a stored credential. {@code id} is the credential ID. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CredentialConnection(
        String id,
        String label,
        CredentialMethod method
) implements Connection {
}
