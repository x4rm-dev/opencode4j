package io.opencode4j.model;

import java.util.Objects;

/** An opaque pagination position returned by the server. Pass it back unchanged; never parse it. */
public record Cursor(String value) {

    public Cursor {
        Objects.requireNonNull(value, "value");
        if (value.isBlank()) {
            throw new IllegalArgumentException("cursor must not be blank");
        }
    }

    /** A cursor for {@code value}, or {@code null} if it is null or blank. */
    public static Cursor ofNullable(String value) {
        return value == null || value.isBlank() ? null : new Cursor(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
