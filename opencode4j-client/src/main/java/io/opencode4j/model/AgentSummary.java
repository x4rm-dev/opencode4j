package io.opencode4j.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Summary information of an agent registered with the server.
 *
 * @param id          unique agent identifier
 * @param name        display name
 * @param description agent role or capabilities description
 * @param mode        "primary" or "subagent"
 * @param system      system prompt / instructions
 * @param steps       maximum execution steps per turn, if configured
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AgentSummary(
        String id,
        String name,
        String description,
        String mode,
        String system,
        Integer steps
) {
}
