package io.opencode4j.model.integration;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/** How an OAuth attempt is completed: automatically or by pasting a code. */
public enum OAuthMode {

    AUTO("auto"),
    CODE("code"),
    /** A value this client version does not recognise. */
    UNKNOWN("unknown");

    private final String wire;

    OAuthMode(String wire) {
        this.wire = wire;
    }

    @JsonValue
    public String wire() {
        return wire;
    }

    @JsonCreator
    public static OAuthMode fromWire(String value) {
        for (OAuthMode candidate : values()) {
            if (candidate.wire.equals(value)) {
                return candidate;
            }
        }
        return UNKNOWN;
    }
}
