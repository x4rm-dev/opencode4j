package io.opencode4j.model.message;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.opencode4j.model.ErrorInfo;
import java.util.Map;

/**
 * Marks the end of one execution, with its outcome. The authoritative record of how a turn ended: after a
 * missed {@code session.execution.*} event, the newest idle entry tells whether the session finished.
 *
 * @param outcome {@code succeeded}, {@code failed} or {@code interrupted}
 * @param error   the failure, when {@code outcome} is {@code failed}
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record IdleMessage(
        String id,
        MessageTime time,
        Map<String, Object> metadata,
        String outcome,
        ErrorInfo error
) implements Message {

    static final String TYPE = "idle";

    public IdleMessage {
        metadata = Messages.metadata(metadata);
    }

    @Override
    public String type() {
        return TYPE;
    }

    public boolean succeeded() {
        return "succeeded".equals(outcome);
    }
}
