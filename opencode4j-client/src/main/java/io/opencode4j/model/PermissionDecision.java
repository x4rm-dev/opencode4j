package io.opencode4j.model;

import com.fasterxml.jackson.annotation.JsonValue;

/** The answer to a permission request. */
public enum PermissionDecision {

    /** Allow this one action. */
    ONCE("once"),
    /** Allow this and matching future actions. */
    ALWAYS("always"),
    /** Deny the action. */
    REJECT("reject");

    private final String wire;

    PermissionDecision(String wire) {
        this.wire = wire;
    }

    @JsonValue
    public String wire() {
        return wire;
    }
}
