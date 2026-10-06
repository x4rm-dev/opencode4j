package io.opencode4j.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Objects;

/**
 * References a registered skill to attach and deterministically inject into a prompt turn.
 *
 * @param id      registered skill identifier
 * @param mention optional character span within the prompt text
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public record PromptSkillAttachment(
        String id,
        PromptMention mention
) {

    public PromptSkillAttachment {
        Objects.requireNonNull(id, "id");
    }

    public static PromptSkillAttachment of(String id) {
        return new PromptSkillAttachment(id, null);
    }

    public static PromptSkillAttachment of(String id, PromptMention mention) {
        return new PromptSkillAttachment(id, mention);
    }
}
