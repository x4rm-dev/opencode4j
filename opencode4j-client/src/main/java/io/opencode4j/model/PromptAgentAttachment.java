package io.opencode4j.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Objects;

/**
 * References a registered sub-agent to attach to a prompt to steer delegation.
 *
 * @param name    registered agent name
 * @param mention optional character span within the prompt text
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public record PromptAgentAttachment(
        String name,
        PromptMention mention
) {

    public PromptAgentAttachment {
        Objects.requireNonNull(name, "name");
    }

    public static PromptAgentAttachment of(String name) {
        return new PromptAgentAttachment(name, null);
    }

    public static PromptAgentAttachment of(String name, PromptMention mention) {
        return new PromptAgentAttachment(name, mention);
    }
}
