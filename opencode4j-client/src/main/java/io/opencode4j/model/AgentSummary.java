package io.opencode4j.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/**
 * Summary information of an agent registered with the server.
 *
 * @param id          unique agent identifier
 * @param name        display name
 * @param description agent role or capabilities description
 * @param mode        "primary" or "subagent"
 * @param system      system prompt / instructions
 * @param steps       maximum execution steps per turn, if configured
 * @param model       the agent's model, or {@code null} for the location default
 * @param permissions the agent's effective permission rules, in evaluation order (the last match wins)
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AgentSummary(
        String id,
        String name,
        String description,
        String mode,
        String system,
        Integer steps,
        ModelRef model,
        List<PermissionRule> permissions
) {

    public AgentSummary {
        permissions = permissions == null ? List.of() : List.copyOf(permissions);
    }

    /** An agent without model or permission details. */
    public AgentSummary(String id, String name, String description, String mode, String system, Integer steps) {
        this(id, name, description, mode, system, steps, null, null);
    }
}
