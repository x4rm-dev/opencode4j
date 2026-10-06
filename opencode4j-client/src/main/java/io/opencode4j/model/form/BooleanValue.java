package io.opencode4j.model.form;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonValue;

/** A boolean form answer, serialized as a bare JSON boolean. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record BooleanValue(@JsonValue boolean value) implements FormValue {
}
