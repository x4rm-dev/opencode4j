package io.opencode4j.model.form;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/** A boolean input field. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record BooleanField(
        String key,
        String title,
        String description,
        boolean required,
        boolean hidden,
        List<FormWhen> when,
        @JsonProperty("default") Boolean defaultValue
) implements FormField {

    public BooleanField {
        when = when == null ? List.of() : List.copyOf(when);
    }
}
