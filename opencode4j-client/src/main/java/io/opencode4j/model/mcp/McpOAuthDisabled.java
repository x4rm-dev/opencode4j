package io.opencode4j.model.mcp;

import com.fasterxml.jackson.annotation.JsonValue;

/** OAuth turned off; serialized as the bare JSON value {@code false}. */
public record McpOAuthDisabled() implements McpOAuth {

    @JsonValue
    public boolean wire() {
        return false;
    }
}
