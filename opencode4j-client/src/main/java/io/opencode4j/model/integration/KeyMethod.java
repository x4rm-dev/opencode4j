package io.opencode4j.model.integration;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.opencode4j.model.form.FormField;
import java.util.List;

/** A method that authenticates with an API key, optionally gathering extra form answers. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record KeyMethod(
        String label,
        List<FormField> form
) implements IntegrationMethod {

    public KeyMethod {
        form = form == null ? List.of() : List.copyOf(form);
    }
}
