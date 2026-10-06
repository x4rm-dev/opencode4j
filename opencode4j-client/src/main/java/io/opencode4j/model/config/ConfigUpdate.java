package io.opencode4j.model.config;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/** How OpenCode handles available updates. */
public enum ConfigUpdate {

    /** Never check for updates. */
    DISABLE("disable"),
    /** Tell the user about updates. */
    NOTIFY("notify"),
    /** Install updates automatically. */
    AUTO("auto"),
    /** A value this client version does not recognise. */
    UNKNOWN("unknown");

    private final String wire;

    ConfigUpdate(String wire) {
        this.wire = wire;
    }

    @JsonValue
    public String wire() {
        return wire;
    }

    @JsonCreator
    public static ConfigUpdate fromWire(String value) {
        for (ConfigUpdate candidate : values()) {
            if (candidate.wire.equals(value)) {
                return candidate;
            }
        }
        return UNKNOWN;
    }
}
