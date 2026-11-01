package io.opencode4j.model.message;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.opencode4j.model.ErrorInfo;
import io.opencode4j.model.ModelRef;
import io.opencode4j.model.TokenCounts;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * One model step: what the agent wrote, how it reasoned, and which tools it called.
 *
 * @param agent  agent that produced the step
 * @param finish {@code stop}, {@code length}, {@code tool-calls}, {@code content-filter}, {@code error} or
 *               {@code unknown}; {@code null} while the step is in progress
 * @param error  the failure, when the step failed
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AssistantMessage(
        String id,
        MessageTime time,
        Map<String, Object> metadata,
        String agent,
        ModelRef model,
        List<AssistantContent> content,
        String finish,
        Double cost,
        TokenCounts tokens,
        ErrorInfo error
) implements Message {

    static final String TYPE = "assistant";

    public AssistantMessage {
        metadata = Messages.metadata(metadata);
        content = content == null ? List.of() : List.copyOf(content);
    }

    @Override
    public String type() {
        return TYPE;
    }

    /** The visible text of this step: its text parts joined in order, without reasoning. */
    public String text() {
        return content.stream()
                .filter(AssistantContent.Text.class::isInstance)
                .map(part -> ((AssistantContent.Text) part).text())
                .collect(Collectors.joining());
    }

    /** The tool calls made in this step, in order. */
    public List<AssistantContent.ToolCall> toolCalls() {
        return content.stream()
                .filter(AssistantContent.ToolCall.class::isInstance)
                .map(AssistantContent.ToolCall.class::cast)
                .toList();
    }
}
