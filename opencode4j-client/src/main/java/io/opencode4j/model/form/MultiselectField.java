package io.opencode4j.model.form;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/** A multiple-choice input field whose answer is a list of strings. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record MultiselectField(
        String key,
        String title,
        String description,
        boolean required,
        boolean hidden,
        List<FormWhen> when,
        List<FormOption> options,
        Integer minItems,
        Integer maxItems,
        Boolean custom,
        @JsonProperty("default") List<String> defaultValue
) implements FormField {

    public MultiselectField {
        when = when == null ? List.of() : List.copyOf(when);
        options = options == null ? List.of() : List.copyOf(options);
        defaultValue = defaultValue == null ? List.of() : List.copyOf(defaultValue);
    }
}
