package io.opencode4j.model.config;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * One configuration source the server looked at: a settings document or a directory it scans.
 *
 * <p>Unrecognised {@code type} values deserialize to {@link UnknownConfigEntry}.</p>
 */
@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.PROPERTY,
        property = "type",
        visible = true,
        defaultImpl = UnknownConfigEntry.class)
@JsonSubTypes({
        @JsonSubTypes.Type(value = ConfigDocument.class, name = "document"),
        @JsonSubTypes.Type(value = ConfigDirectory.class, name = "directory")
})
public sealed interface ConfigEntry permits ConfigDocument, ConfigDirectory, UnknownConfigEntry {
}
