package io.opencode4j.model.integration;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.opencode4j.model.form.FormField;
import java.util.List;

/** An OAuth method. {@code id} is the {@code methodID} to pass when connecting. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OAuthMethod(
        String id,
        String label,
        List<FormField> form
) implements IntegrationMethod {

    public OAuthMethod {
        form = form == null ? List.of() : List.copyOf(form);
    }
}
