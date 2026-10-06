package io.opencode4j.model.mcp;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/** The MCP protocol revision to speak with a server. */
public enum McpProtocol {

    /** The legacy protocol. */
    LEGACY("legacy"),
    /** Negotiate the revision. */
    AUTO("auto"),
    /** Protocol revision 2026-07-28. */
    V2026_07_28("2026-07-28"),
    /** A value this client version does not recognise. */
    UNKNOWN("unknown");

    private final String wire;

    McpProtocol(String wire) {
        this.wire = wire;
    }

    @JsonValue
    public String wire() {
        return wire;
    }

    @JsonCreator
    public static McpProtocol fromWire(String value) {
        for (McpProtocol candidate : values()) {
            if (candidate.wire.equals(value)) {
                return candidate;
            }
        }
        return UNKNOWN;
    }
}
