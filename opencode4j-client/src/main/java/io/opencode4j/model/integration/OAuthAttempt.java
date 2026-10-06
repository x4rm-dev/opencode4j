package io.opencode4j.model.integration;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** A started OAuth attempt: open {@code url}, then complete it according to {@code mode}. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OAuthAttempt(
        String attemptID,
        String url,
        String instructions,
        OAuthMode mode,
        AttemptTime time
) {
}
