package io.opencode4j.model.integration;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.opencode4j.model.form.FormValue;
import java.util.Map;
import java.util.Objects;

/** Request to connect an integration with an API key. */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public record ConnectKeyRequest(
        String key,
        Map<String, FormValue> answer,
        String label
) {

    public ConnectKeyRequest {
        Objects.requireNonNull(key, "key");
    }

    public static ConnectKeyRequest of(String key) {
        return new ConnectKeyRequest(key, null, null);
    }
}
