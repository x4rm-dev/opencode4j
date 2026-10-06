package io.opencode4j.model.form;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** A selectable option of a string or multiselect field. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record FormOption(
        String value,
        String label,
        String description
) {
}
