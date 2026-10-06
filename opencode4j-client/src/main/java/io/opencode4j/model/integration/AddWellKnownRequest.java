package io.opencode4j.model.integration;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Objects;

/** Request to add an experimental well-known integration source discovered from {@code url}. */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public record AddWellKnownRequest(
        String url
) {

    public AddWellKnownRequest {
        Objects.requireNonNull(url, "url");
    }

    public static AddWellKnownRequest of(String url) {
        return new AddWellKnownRequest(url);
    }
}
