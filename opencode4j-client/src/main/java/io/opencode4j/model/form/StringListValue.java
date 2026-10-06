package io.opencode4j.model.form;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.List;

/** A string-list form answer, serialized as a bare JSON array. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record StringListValue(@JsonValue List<String> value) implements FormValue {

    public StringListValue {
        value = value == null ? List.of() : List.copyOf(value);
    }
}
