package io.opencode4j.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * An OpenCode session.
 *
 * @param metadata caller-supplied metadata; empty if none
 * @param outcome  how the latest execution ended ({@code succeeded}, {@code failed}, {@code interrupted}), or
 *                 {@code null} if the session never ran
 */
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
        LocationRef location,
        Map<String, Object> metadata,
        String outcome
) {

    public Session {
        metadata = metadata == null || metadata.isEmpty()
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(metadata));
    }
}
