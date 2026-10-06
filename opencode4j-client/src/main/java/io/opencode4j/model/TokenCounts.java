package io.opencode4j.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Token counters exactly as the server reports them on sessions and step events. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TokenCounts(
        long input,
        long output,
        long reasoning,
        CacheCounts cache
) {

    public TokenCounts {
        cache = cache == null ? new CacheCounts(0, 0) : cache;
    }
}
