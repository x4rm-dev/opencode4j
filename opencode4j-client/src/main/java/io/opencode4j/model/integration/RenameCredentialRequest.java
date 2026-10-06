package io.opencode4j.model.integration;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Objects;

/** Request to change the display label of a stored credential. */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public record RenameCredentialRequest(
        String label
) {

    public RenameCredentialRequest {
        Objects.requireNonNull(label, "label");
    }

    public static RenameCredentialRequest of(String label) {
        return new RenameCredentialRequest(label);
    }
}
