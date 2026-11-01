package io.opencode4j.model;

/**
 * Which page of a listing to read.
 *
 * @param limit  maximum number of items, or {@code null} for the server default
 * @param order  sort order, or {@code null} for the server default (newest first)
 * @param cursor position to continue from (a {@link Page#next()} or {@link Page#previous()}), or {@code null}
 *               for the first page
 */
public record PageRequest(
        Integer limit,
        SortOrder order,
        Cursor cursor
) {

    public PageRequest {
        if (limit != null && limit <= 0) {
            throw new IllegalArgumentException("limit must be positive: " + limit);
        }
    }

    /** The first page with the server's default size and order. */
    public static PageRequest first() {
        return new PageRequest(null, null, null);
    }

    /** The first page of at most {@code limit} items. */
    public static PageRequest first(int limit) {
        return new PageRequest(limit, null, null);
    }

    /** The same request continuing from {@code cursor}. */
    public PageRequest at(Cursor cursor) {
        return new PageRequest(limit, order, cursor);
    }

    /** The same request in {@code order}. */
    public PageRequest ordered(SortOrder order) {
        return new PageRequest(limit, order, cursor);
    }
}
