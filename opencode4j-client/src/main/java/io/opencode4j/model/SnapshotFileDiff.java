package io.opencode4j.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record SnapshotFileDiff(
        String file,
        String patch,
        Integer additions,
        Integer deletions,
        String status
) { }
