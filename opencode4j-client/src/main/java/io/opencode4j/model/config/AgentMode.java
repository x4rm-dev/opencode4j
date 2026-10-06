package io.opencode4j.model.config;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/** Where an agent can be used. */
public enum AgentMode {

    /** Only as a sub-agent. */
    SUBAGENT("subagent"),
    /** Only as a primary agent. */
    PRIMARY("primary"),
    /** Both. */
    ALL("all"),
    /** A value this client version does not recognise. */
    UNKNOWN("unknown");

    private final String wire;

    AgentMode(String wire) {
        this.wire = wire;
    }

    @JsonValue
    public String wire() {
        return wire;
    }

    @JsonCreator
    public static AgentMode fromWire(String value) {
        for (AgentMode candidate : values()) {
            if (candidate.wire.equals(value)) {
                return candidate;
            }
        }
        return UNKNOWN;
    }
}
