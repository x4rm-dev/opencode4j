package io.opencode4j.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * A structured error reported by the server ({@code Session.StructuredError}).
 *
 * @param type         error class, for example {@code provider.auth}, {@code aborted} or {@code compaction.failed}
 * @param message      human-readable message
 * @param status       HTTP status of an upstream provider failure, or {@code null}
 * @param retryAfterMs delay the provider asked for before retrying, or {@code null}
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ErrorInfo(
        String type,
        String message,
        Integer status,
        Long retryAfterMs
) {
}
