package io.opencode4j.model.form;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/** A field whose {@code type} this client version does not recognise. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record UnknownField(
        String type,
        String key,
        String title,
        String description,
        boolean required,
        boolean hidden,
        List<FormWhen> when
) implements FormField {

    public UnknownField {
        when = when == null ? List.of() : List.copyOf(when);
    }
}
