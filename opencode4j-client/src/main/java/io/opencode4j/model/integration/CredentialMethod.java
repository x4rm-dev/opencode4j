package io.opencode4j.model.integration;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/** How a stored credential was obtained. */
public enum CredentialMethod {

    KEY("key"),
    OAUTH("oauth"),
    /** A value this client version does not recognise. */
    UNKNOWN("unknown");

    private final String wire;

    CredentialMethod(String wire) {
        this.wire = wire;
    }

    @JsonValue
    public String wire() {
        return wire;
    }

    @JsonCreator
    public static CredentialMethod fromWire(String value) {
        for (CredentialMethod candidate : values()) {
            if (candidate.wire.equals(value)) {
                return candidate;
            }
        }
        return UNKNOWN;
    }
}
