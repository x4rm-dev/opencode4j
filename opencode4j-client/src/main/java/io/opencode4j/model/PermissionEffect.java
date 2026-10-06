package io.opencode4j.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/** What a permission rule does when it matches. */
public enum PermissionEffect {

    /** Allow the action without asking. */
    ALLOW("allow"),
    /** Refuse the action. */
    DENY("deny"),
    /** Ask the user. */
    ASK("ask"),
    /** A value this client version does not recognise. */
    UNKNOWN("unknown");

    private final String wire;

    PermissionEffect(String wire) {
        this.wire = wire;
    }

    @JsonValue
    public String wire() {
        return wire;
    }

    @JsonCreator
    public static PermissionEffect fromWire(String value) {
        for (PermissionEffect candidate : values()) {
            if (candidate.wire.equals(value)) {
                return candidate;
            }
        }
        return UNKNOWN;
    }
}
