package io.opencode4j.model.form;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/** A text input field. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record StringField(
        String key,
        String title,
        String description,
        boolean required,
        boolean hidden,
        List<FormWhen> when,
        String format,
        Integer minLength,
        Integer maxLength,
        String pattern,
        String placeholder,
        @JsonProperty("default") String defaultValue,
        List<FormOption> options,
        Boolean custom
) implements FormField {

    public StringField {
        when = when == null ? List.of() : List.copyOf(when);
        options = options == null ? List.of() : List.copyOf(options);
    }
}
