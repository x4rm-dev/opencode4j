package io.opencode4j.model.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Enterprise settings. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ConfigEnterprise(
        String url
) {
}
