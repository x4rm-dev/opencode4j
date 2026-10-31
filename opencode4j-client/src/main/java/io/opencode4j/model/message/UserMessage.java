package io.opencode4j.model.message;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.Map;

/** A prompt sent to the session. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record UserMessage(
        String id,
        MessageTime time,
        Map<String, Object> metadata,
        String text
) implements Message {

    static final String TYPE = "user";

    public UserMessage {
        metadata = Messages.metadata(metadata);
    }

    @Override
    public String type() {
        return TYPE;
    }
}
