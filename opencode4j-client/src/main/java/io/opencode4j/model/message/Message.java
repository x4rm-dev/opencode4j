package io.opencode4j.model.message;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import java.util.Map;

/**
 * One entry of a session's history ({@code GET /api/session/{id}/message}).
 *
 * <p>The history is a sequence of typed entries: user prompts, assistant turns (text, reasoning and tool
 * calls), {@code idle} markers that close each execution with its outcome, compactions, and bookkeeping
 * entries. Types this library does not model decode to {@link OtherMessage}, so a newer server never breaks
 * history reads.</p>
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type", visible = true, defaultImpl = OtherMessage.class)
@JsonSubTypes({
        @JsonSubTypes.Type(value = UserMessage.class, name = UserMessage.TYPE),
        @JsonSubTypes.Type(value = AssistantMessage.class, name = AssistantMessage.TYPE),
        @JsonSubTypes.Type(value = IdleMessage.class, name = IdleMessage.TYPE),
        @JsonSubTypes.Type(value = CompactionMessage.class, name = CompactionMessage.TYPE),
        @JsonSubTypes.Type(value = SyntheticMessage.class, name = SyntheticMessage.TYPE),
        @JsonSubTypes.Type(value = SystemMessage.class, name = SystemMessage.TYPE)
})
public sealed interface Message
        permits UserMessage, AssistantMessage, IdleMessage, CompactionMessage, SyntheticMessage, SystemMessage,
        OtherMessage {

    /** Message id, for example {@code msg_...}. Ids sort in creation order. */
    String id();

    /** The wire type, for example {@code "assistant"}. */
    String type();

    /** Timestamps in epoch milliseconds. */
    MessageTime time();

    /** Caller-supplied metadata, such as the metadata given with a prompt. Empty if none. */
    Map<String, Object> metadata();
}
