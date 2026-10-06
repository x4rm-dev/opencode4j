package io.opencode4j.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Objects;

/** Request body for replying to a permission request. {@code message} is an optional note for the agent. */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public record PermissionReplyRequest(
        PermissionDecision decision,
        String message
) {

    public PermissionReplyRequest {
        Objects.requireNonNull(decision, "decision");
    }

    public static PermissionReplyRequest of(PermissionDecision decision) {
        return new PermissionReplyRequest(decision, null);
    }
}
