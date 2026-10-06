package io.opencode4j.model.integration;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * A configured connection of an integration.
 *
 * <p>Unrecognised {@code type} values deserialize to {@link UnknownConnection}.</p>
 */
@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.PROPERTY,
        property = "type",
        visible = true,
        defaultImpl = UnknownConnection.class)
@JsonSubTypes({
        @JsonSubTypes.Type(value = CredentialConnection.class, name = "credential"),
        @JsonSubTypes.Type(value = EnvConnection.class, name = "env")
})
public sealed interface Connection permits CredentialConnection, EnvConnection, UnknownConnection {
}
