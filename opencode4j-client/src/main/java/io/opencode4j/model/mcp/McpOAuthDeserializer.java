package io.opencode4j.model.mcp;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import java.io.IOException;

/** Reads the {@code oauth} property of a remote MCP server: {@code false} or a configuration object. */
public final class McpOAuthDeserializer extends JsonDeserializer<McpOAuth> {

    @Override
    public McpOAuth deserialize(JsonParser parser, DeserializationContext context) throws IOException {
        JsonToken token = parser.currentToken();
        if (token == JsonToken.VALUE_FALSE) {
            return new McpOAuthDisabled();
        }
        if (token == JsonToken.START_OBJECT) {
            return context.readValue(parser, McpOAuthConfig.class);
        }
        return (McpOAuth) context.handleUnexpectedToken(McpOAuth.class, parser);
    }
}
