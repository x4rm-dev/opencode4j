package io.opencode4j.model.message;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.Map;

/** Text injected into the conversation on the user side by the caller or the server, not typed by a user. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SyntheticMessage(
        String id,
        MessageTime time,
        Map<String, Object> metadata,
        String text,
        String description
) implements Message {

    static final String TYPE = "synthetic";

    public SyntheticMessage {
        metadata = Messages.metadata(metadata);
    }

    @Override
    public String type() {
        return TYPE;
    }
}
