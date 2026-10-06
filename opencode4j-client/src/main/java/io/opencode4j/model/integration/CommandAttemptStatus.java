package io.opencode4j.model.integration;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Current status of a command authentication attempt.
 *
 * <p>{@code message} may accompany a pending attempt (progress) or a failed one (the reason).</p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CommandAttemptStatus(
        AttemptState status,
        String message,
        AttemptTime time
) {
}
