package io.opencode4j.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Prompt-cache token counters. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CacheCounts(
        long read,
        long write
) {
}
