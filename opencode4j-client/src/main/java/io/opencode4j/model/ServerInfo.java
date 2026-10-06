package io.opencode4j.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import java.util.Map;

/** Identity and readiness information returned by {@code GET /api/info}. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ServerInfo(
        String version,
        Integer pid,
        List<String> urls,
        Map<String, String> paths
) {

    public ServerInfo {
        urls = urls == null ? List.of() : List.copyOf(urls);
        paths = paths == null ? Map.of() : Map.copyOf(paths);
    }
}
