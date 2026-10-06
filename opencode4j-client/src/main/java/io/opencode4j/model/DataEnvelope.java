package io.opencode4j.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Generic container for OpenCode v2 responses wrapped in a {@code "data"} property. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record DataEnvelope<T>(T data) { }
