package io.opencode4j.event;

import io.opencode4j.model.form.FormField;
import java.util.List;

/**
 * The agent asks the user to fill in a form, for example through the question tool
 * ({@code kind == "question"}). Answer with {@code InteractionApi.replyForm} or dismiss with
 * {@code InteractionApi.cancelForm}.
 */
public record FormCreated(
        String id,
        String sessionId,
        String title,
        String kind,
        List<FormField> fields
) implements OpenCodeEvent {

    public FormCreated {
        fields = fields == null ? List.of() : List.copyOf(fields);
    }

    @Override
    public String type() {
        return "form.created";
    }
}
