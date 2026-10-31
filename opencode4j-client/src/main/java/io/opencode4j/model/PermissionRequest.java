package io.opencode4j.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;

/**
 * A pending permission request: the agent wants to perform {@code action} on {@code resources} and waits for
 * an answer.
 *
 * @param action    permission action, for example {@code shell}, {@code edit} or {@code webfetch}
 * @param resources what the action applies to, for example the shell command
 * @param save      the patterns an {@code always} answer would remember
 * @param source    the tool call that raised the request, or {@code null}
 * @param message   an explanation from the agent, or {@code null}
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PermissionRequest(
        String id,
        @JsonProperty("sessionID") String sessionId,
        String action,
        List<String> resources,
        List<String> save,
        Map<String, Object> metadata,
        Source source,
        String message
) {

    public PermissionRequest {
        resources = resources == null ? List.of() : List.copyOf(resources);
        save = save == null ? List.of() : List.copyOf(save);
        metadata = metadata == null ? Map.of() : metadata;
    }

    /**
     * Where a request came from.
     *
     * @param type      {@code tool}
     * @param messageId the assistant message that made the call
     * @param callId    the tool call id
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Source(
            String type,
            @JsonProperty("messageID") String messageId,
            @JsonProperty("id") String callId
    ) {
    }
}
