package io.opencode4j.model.message;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.opencode4j.model.ErrorInfo;
import io.opencode4j.model.ModelRef;
import io.opencode4j.model.TokenCounts;
import java.util.Map;

/**
 * A context compaction. When {@code status} is {@code completed}, {@code summary} replaces the history before
 * it in what the model sees ({@code GET /api/session/{id}/context}).
 *
 * @param status {@code running}, {@code completed} or {@code failed}
 * @param reason {@code auto} or {@code manual}
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CompactionMessage(
        String id,
        MessageTime time,
        Map<String, Object> metadata,
        String status,
        String reason,
        String summary,
        ModelRef model,
        Double cost,
        TokenCounts tokens,
        ErrorInfo error
) implements Message {

    static final String TYPE = "compaction";

    public CompactionMessage {
        metadata = Messages.metadata(metadata);
    }

    @Override
    public String type() {
        return TYPE;
    }
}
