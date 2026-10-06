package io.opencode4j.model.file;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/** Whether a file system entry is a file or a directory. */
public enum FileType {

    /** A regular file. */
    FILE("file"),
    /** A directory. */
    DIRECTORY("directory"),
    /** A value this client version does not recognise. */
    UNKNOWN("unknown");

    private final String wire;

    FileType(String wire) {
        this.wire = wire;
    }

    @JsonValue
    public String wire() {
        return wire;
    }

    @JsonCreator
    public static FileType fromWire(String value) {
        for (FileType candidate : values()) {
            if (candidate.wire.equals(value)) {
                return candidate;
            }
        }
        return UNKNOWN;
    }
}
