package io.opencode4j.model.mcp;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.opencode4j.model.Immutable;
import java.util.Map;
import java.util.Objects;

/** A remote MCP server reached over HTTP. */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@JsonIgnoreProperties(ignoreUnknown = true)
public record McpRemoteServerConfig(
        String url,
        Map<String, String> headers,
        @JsonDeserialize(using = McpOAuthDeserializer.class) McpOAuth oauth,
        Boolean disabled,
        Boolean codemode,
        McpTimeout timeout,
        McpProtocol protocol
) implements McpServerConfig {

    public McpRemoteServerConfig {
        Objects.requireNonNull(url, "url");
        headers = Immutable.map(headers);
    }
}
