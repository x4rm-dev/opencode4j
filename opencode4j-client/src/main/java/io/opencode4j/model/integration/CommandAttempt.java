package io.opencode4j.model.integration;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** A started command authentication attempt. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CommandAttempt(
        String attemptID,
        AttemptTime time
) {
}
