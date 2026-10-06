package io.opencode4j.model.integration;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * An authentication method offered by an integration.
 *
 * <p>Unrecognised {@code type} values deserialize to {@link UnknownMethod}.</p>
 */
@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.PROPERTY,
        property = "type",
        visible = true,
        defaultImpl = UnknownMethod.class)
@JsonSubTypes({
        @JsonSubTypes.Type(value = OAuthMethod.class, name = "oauth"),
        @JsonSubTypes.Type(value = CommandMethod.class, name = "command"),
        @JsonSubTypes.Type(value = KeyMethod.class, name = "key"),
        @JsonSubTypes.Type(value = EnvMethod.class, name = "env")
})
public sealed interface IntegrationMethod
        permits OAuthMethod, CommandMethod, KeyMethod, EnvMethod, UnknownMethod {
}
