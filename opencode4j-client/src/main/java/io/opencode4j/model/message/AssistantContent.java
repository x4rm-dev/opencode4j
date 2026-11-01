package io.opencode4j.model.message;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.JsonNode;
import io.opencode4j.model.ErrorInfo;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** One part of an {@link AssistantMessage}. Unknown part types decode to {@link Other}. */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type", visible = true,
        defaultImpl = AssistantContent.Other.class)
@JsonSubTypes({
        @JsonSubTypes.Type(value = AssistantContent.Text.class, name = "text"),
        @JsonSubTypes.Type(value = AssistantContent.Reasoning.class, name = "reasoning"),
        @JsonSubTypes.Type(value = AssistantContent.ToolCall.class, name = "tool")
})
public sealed interface AssistantContent {

    /** Text the model wrote. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record Text(String text) implements AssistantContent {
    }

    /** The model's reasoning, where the provider exposes it. Not meant for end users. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record Reasoning(String text) implements AssistantContent {
    }

    /**
     * A tool call and its current state.
     *
     * @param id       call id; matches the {@code id} of {@code session.tool.*} events
     * @param name     tool name, for example {@code shell} or {@code victorialogs_query}
     * @param executed whether the tool actually ran (a declined call did not)
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record ToolCall(String id, String name, Boolean executed, ToolState state) implements AssistantContent {
    }

    /** A part type this library does not model. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record Other(String type) implements AssistantContent {
    }

    /**
     * Where a tool call is in its lifecycle.
     *
     * @param status  {@code streaming}, {@code running}, {@code completed} or {@code error}
     * @param input   the call arguments: a partial JSON string while {@code streaming}, an object afterwards
     * @param content the tool's output parts, once completed (and sometimes on error)
     * @param error   the failure, when {@code status} is {@code error}
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record ToolState(
            String status,
            JsonNode input,
            List<ToolOutput> content,
            ErrorInfo error,
            Map<String, Object> metadata
    ) {

        public ToolState {
            content = content == null ? List.of() : List.copyOf(content);
            metadata = Messages.metadata(metadata);
        }

        /** The text output parts joined with newlines. */
        public String outputText() {
            return content.stream()
                    .map(ToolOutput::text)
                    .filter(text -> text != null)
                    .collect(Collectors.joining("\n"));
        }
    }

    /**
     * One output part of a tool call: {@code text} with {@code text}, or {@code file} with {@code uri},
     * {@code mime} and an optional {@code name}.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record ToolOutput(String type, String text, String uri, String mime, String name) {
    }
}
