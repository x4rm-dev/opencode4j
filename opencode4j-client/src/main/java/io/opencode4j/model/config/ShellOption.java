package io.opencode4j.model.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** A shell installed on the server's machine. {@code acceptable} is whether OpenCode can use it. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ShellOption(
        String path,
        String name,
        boolean acceptable
) {
}
