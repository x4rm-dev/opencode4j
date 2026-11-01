package io.opencode4j.model.message;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Message timestamps in epoch milliseconds. Only {@code created} is always present; assistant messages also
 * report when streaming started and when the message completed.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record MessageTime(
        Long created,
        Long streamed,
        Long completed
) {
}
