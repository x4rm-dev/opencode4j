package io.opencode4j.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Session timestamps in epoch milliseconds. Only {@code created} and {@code updated} are always present. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SessionTime(
        Long created,
        Long updated,
        Long idle,
        Long viewed,
        Long archived
) {
}
