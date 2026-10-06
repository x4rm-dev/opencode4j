package io.opencode4j.model.form;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/** Comparison operator used by a {@link FormWhen} condition. */
public enum FormOperator {

    EQ("eq"),
    NEQ("neq"),
    /** An operator this client version does not recognise. */
    UNKNOWN("unknown");

    private final String wire;

    FormOperator(String wire) {
        this.wire = wire;
    }

    @JsonValue
    public String wire() {
        return wire;
    }

    @JsonCreator
    public static FormOperator fromWire(String value) {
        for (FormOperator candidate : values()) {
            if (candidate.wire.equals(value)) {
                return candidate;
            }
        }
        return UNKNOWN;
    }
}
