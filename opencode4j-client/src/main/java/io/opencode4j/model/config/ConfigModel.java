package io.opencode4j.model.config;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Map;
import java.util.Objects;

/**
 * A model named in a configuration file. The file may write it as {@code "provider/model"} or as an object;
 * both forms are accepted, and it is always written back as an object.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public record ConfigModel(
        String providerID,
        String model,
        String variant
) {

    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    static ConfigModel fromJson(Object value) {
        if (value instanceof String text) {
            return parse(text);
        }
        if (value instanceof Map<?, ?> fields) {
            return new ConfigModel(text(fields, "providerID"), text(fields, "model"), text(fields, "variant"));
        }
        throw new IllegalArgumentException("A model must be a string or an object but was: " + value);
    }

    /** Parses {@code "provider/model"}; everything after the first slash is the model. */
    public static ConfigModel parse(String value) {
        Objects.requireNonNull(value, "value");
        int slash = value.indexOf('/');
        if (slash <= 0 || slash == value.length() - 1) {
            throw new IllegalArgumentException("Expected \"provider/model\" but got: " + value);
        }
        return new ConfigModel(value.substring(0, slash), value.substring(slash + 1), null);
    }

    private static String text(Map<?, ?> fields, String key) {
        Object value = fields.get(key);
        return value == null ? null : value.toString();
    }
}
