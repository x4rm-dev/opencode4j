package io.opencode4j.model.config;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Request body of the global configuration update. The server currently accepts only the shell.
 *
 * <p>{@code shell} is always sent, including as an explicit {@code null}, which clears the setting.</p>
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record ConfigPatch(
        String shell
) {
}
