package io.opencode4j.model.form;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/** A step completed outside the form, for example in a browser at {@code url}. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ExternalField(
        String key,
        String url,
        String title,
        String description
) implements FormField {

    @Override
    public boolean required() {
        return false;
    }

    @Override
    public boolean hidden() {
        return false;
    }

    @Override
    public List<FormWhen> when() {
        return List.of();
    }
}
