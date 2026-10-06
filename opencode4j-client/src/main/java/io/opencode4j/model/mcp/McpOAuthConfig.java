package io.opencode4j.model.mcp;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/** OAuth client settings for a remote MCP server. */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public record McpOAuthConfig(
        @JsonProperty("client_id") String clientId,
        @JsonProperty("client_secret") String clientSecret,
        String scope,
        @JsonProperty("callback_port") Integer callbackPort,
        @JsonProperty("redirect_uri") String redirectUri,
        @JsonProperty("auth_server_metadata_url") String authServerMetadataUrl
) implements McpOAuth {
}
