package io.opencode4j.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Map;

/**
 * Request to create a session. Every field is optional; the server fills in defaults.
 *
 * <p>In the v2 API the agent and model are properties of the session, not of each prompt.</p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public record CreateSessionRequest(
        String title,
        String agent,
        ModelRef model,
        LocationRef location,
        Map<String, Object> metadata
) {

    public CreateSessionRequest(
            String title,
            String agent,
            ModelRef model,
            LocationRef location
    ) {
        this(title, agent, model, location, null);
    }

    public static CreateSessionRequest empty() {
        return new CreateSessionRequest(null, null, null, null, null);
    }

    public static CreateSessionRequest titled(String title) {
        return new CreateSessionRequest(title, null, null, null, null);
    }
}
