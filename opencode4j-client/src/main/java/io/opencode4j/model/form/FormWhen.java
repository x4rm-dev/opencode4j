package io.opencode4j.model.form;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Conditional visibility rule for a form field.
 *
 * <p>{@code value} is {@link Object} because the wire schema is a scalar union
 * (string, number, boolean or null) that Java cannot express as a single type.</p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record FormWhen(
        String key,
        FormOperator op,
        Object value
) {
}
