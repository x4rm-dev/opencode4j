package io.opencode4j.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/** How a prompt reaches a session that is already working. */
public enum Delivery {

    /** Injected into the running turn. */
    STEER("steer"),
    /** Held until the running turn finishes. */
    QUEUE("queue"),
    /** A value this client version does not recognise. */
    UNKNOWN("unknown");

    private final String wire;

    Delivery(String wire) {
        this.wire = wire;
    }

    @JsonValue
    public String wire() {
        return wire;
    }

    @JsonCreator
    public static Delivery fromWire(String value) {
        for (Delivery candidate : values()) {
            if (candidate.wire.equals(value)) {
                return candidate;
            }
        }
        return UNKNOWN;
    }
}
