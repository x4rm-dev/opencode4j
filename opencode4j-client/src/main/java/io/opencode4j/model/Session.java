package io.opencode4j.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/** An OpenCode session. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record Session(
        String id,
        @JsonProperty("parentID") String parentId,
        @JsonProperty("projectID") String projectId,
        String title,
        String agent,
        ModelRef model,
        Double cost,
        TokenCounts tokens,
        SessionTime time,
        LocationRef location
) {
}
