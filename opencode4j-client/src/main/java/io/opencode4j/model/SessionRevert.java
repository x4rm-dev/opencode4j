package io.opencode4j.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** A staged revert of a session. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SessionRevert(
        String messageID,
        String partID,
        String snapshot
) {
}
