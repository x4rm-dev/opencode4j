package io.opencode4j.model.form;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/** A floating-point numeric input field. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record NumberField(
        String key,
        String title,
        String description,
        boolean required,
        boolean hidden,
        List<FormWhen> when,
        Double minimum,
        Double maximum,
        @JsonProperty("default") Double defaultValue
) implements FormField {

    public NumberField {
        when = when == null ? List.of() : List.copyOf(when);
    }
}
