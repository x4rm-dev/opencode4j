package io.opencode4j.model.message;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.Map;

/**
 * A history entry of a type this library does not model, such as {@code agent-switched}, {@code skill} or
 * {@code shell}. Only the common fields are decoded.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OtherMessage(
        String id,
        String type,
        MessageTime time,
        Map<String, Object> metadata
) implements Message {

    public OtherMessage {
        metadata = Messages.metadata(metadata);
    }
}
