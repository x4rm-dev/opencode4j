package io.opencode4j.model.integration;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.opencode4j.model.form.FormValue;
import java.util.Map;
import java.util.Objects;

/** Request to start an OAuth connection using the method identified by {@code methodID}. */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public record ConnectOAuthRequest(
        String methodID,
        Map<String, FormValue> answer,
        String label
) {

    public ConnectOAuthRequest {
        Objects.requireNonNull(methodID, "methodID");
    }

    public static ConnectOAuthRequest of(String methodID) {
        return new ConnectOAuthRequest(methodID, null, null);
    }
}
