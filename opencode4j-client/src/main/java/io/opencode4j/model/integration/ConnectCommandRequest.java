package io.opencode4j.model.integration;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Objects;

/** Request to start a command-based connection using the method identified by {@code methodID}. */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public record ConnectCommandRequest(
        String methodID,
        String label
) {

    public ConnectCommandRequest {
        Objects.requireNonNull(methodID, "methodID");
    }

    public static ConnectCommandRequest of(String methodID) {
        return new ConnectCommandRequest(methodID, null);
    }
}
