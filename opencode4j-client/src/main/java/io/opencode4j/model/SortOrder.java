package io.opencode4j.model;

/** Order of a paginated listing by creation time. */
public enum SortOrder {
    /** Oldest first. */
    ASC("asc"),
    /** Newest first; the server default. */
    DESC("desc");

    private final String wire;

    SortOrder(String wire) {
        this.wire = wire;
    }

    public String wire() {
        return wire;
    }
}
