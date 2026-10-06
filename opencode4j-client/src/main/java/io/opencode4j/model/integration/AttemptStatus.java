package io.opencode4j.model.integration;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Current status of an OAuth attempt. {@code message} is present only when the attempt failed. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AttemptStatus(
        AttemptState status,
        String message,
        AttemptTime time
) {
}
