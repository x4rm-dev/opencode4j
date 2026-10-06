package io.opencode4j.model.file;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * A file or directory.
 *
 * @param path relative to the location; directories end with {@code /}
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record FileEntry(
        String path,
        FileType type
) {
}
