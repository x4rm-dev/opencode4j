package io.opencode4j.model.config;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/** How session sharing behaves. */
public enum ConfigShare {

    /** Share only on request. */
    MANUAL("manual"),
    /** Share automatically. */
    AUTO("auto"),
    /** Sharing is turned off. */
    DISABLED("disabled"),
    /** A value this client version does not recognise. */
    UNKNOWN("unknown");

    private final String wire;

    ConfigShare(String wire) {
        this.wire = wire;
    }

    @JsonValue
    public String wire() {
        return wire;
    }

    @JsonCreator
    public static ConfigShare fromWire(String value) {
        for (ConfigShare candidate : values()) {
            if (candidate.wire.equals(value)) {
                return candidate;
            }
        }
        return UNKNOWN;
    }
}
