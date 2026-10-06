package io.opencode4j.model.form;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonValue;

/** A string form answer, serialized as a bare JSON string. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record StringValue(@JsonValue String value) implements FormValue {
}
