package io.opencode4j.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Objects;

/**
 * Character span within prompt text that explicitly mentions a skill or sub-agent.
 *
 * @param start 0-based character start index in prompt text
 * @param end   0-based character end index in prompt text (exclusive)
 * @param text  the raw mention text, e.g. {@code "@launch-protocol"}
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public record PromptMention(
        int start,
        int end,
        String text
) {

    public PromptMention {
        if (start < 0) {
            throw new IllegalArgumentException("start must be >= 0: " + start);
        }
        if (end < start) {
            throw new IllegalArgumentException("end must be >= start (" + start + "): " + end);
        }
        Objects.requireNonNull(text, "text");
    }

    public static PromptMention of(int start, int end, String text) {
        return new PromptMention(start, end, text);
    }
}
