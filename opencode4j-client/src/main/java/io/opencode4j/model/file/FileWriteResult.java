package io.opencode4j.model.file;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** The outcome of a file write. {@code path} is the absolute path the server resolved. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record FileWriteResult(
        String path
) {
}
