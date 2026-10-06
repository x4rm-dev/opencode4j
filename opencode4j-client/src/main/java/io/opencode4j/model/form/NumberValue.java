package io.opencode4j.model.form;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonValue;

/** A numeric form answer, serialized as a bare JSON number. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record NumberValue(@JsonValue double value) implements FormValue {
}
