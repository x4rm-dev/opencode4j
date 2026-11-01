package io.opencode4j.model.message;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.Map;

/** A system notice recorded in the history. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SystemMessage(
        String id,
        MessageTime time,
        Map<String, Object> metadata,
        String text,
        String description
) implements Message {

    static final String TYPE = "system";

    public SystemMessage {
        metadata = Messages.metadata(metadata);
    }

    @Override
    public String type() {
        return TYPE;
    }
}
