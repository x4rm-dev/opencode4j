package io.opencode4j.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Objects;

/** A provider model reference, for example {@code github-copilot/gpt-6.1-sol}. */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public record ModelRef(
        String id,
        String providerID,
        String variant
) {

    public ModelRef {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(providerID, "providerID");
    }

    /**
     * Parses {@code "provider/model"}. Everything after the first slash is the model ID, so model IDs
     * that themselves contain slashes are supported.
     *
     * @throws IllegalArgumentException if the value has no provider part
     */
    public static ModelRef parse(String value) {
        Objects.requireNonNull(value, "value");
        int slash = value.indexOf('/');
        if (slash <= 0 || slash == value.length() - 1) {
            throw new IllegalArgumentException("Expected \"provider/model\" but got: " + value);
        }
        return new ModelRef(value.substring(slash + 1), value.substring(0, slash), null);
    }

    @Override
    public String toString() {
        return providerID + "/" + id;
    }
}
