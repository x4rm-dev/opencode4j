package io.opencode4j.model.integration;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/** State of an integration authentication attempt. */
public enum AttemptState {

    PENDING("pending"),
    COMPLETE("complete"),
    FAILED("failed"),
    EXPIRED("expired"),
    /** A value this client version does not recognise. */
    UNKNOWN("unknown");

    private final String wire;

    AttemptState(String wire) {
        this.wire = wire;
    }

    @JsonValue
    public String wire() {
        return wire;
    }

    @JsonCreator
    public static AttemptState fromWire(String value) {
        for (AttemptState candidate : values()) {
            if (candidate.wire.equals(value)) {
                return candidate;
            }
        }
        return UNKNOWN;
    }
}
