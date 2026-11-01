package io.opencode4j.model;

import java.util.List;

/**
 * One page of a listing.
 *
 * <p>The server returns a {@code next} cursor even on the last page; the listing is exhausted when a page
 * comes back empty. {@link #hasMore(PageRequest)} applies that rule plus the cheaper "short page" check.</p>
 *
 * @param items    the items on this page, in the requested order
 * @param next     cursor for the following page, or {@code null}
 * @param previous cursor for the preceding page, or {@code null}
 */
public record Page<T>(
        List<T> items,
        Cursor next,
        Cursor previous
) {

    public Page {
        items = items == null ? List.of() : List.copyOf(items);
    }

    /**
     * Whether reading {@link #next()} may return more items: there is a cursor, and this page is non-empty
     * and, when the request had a limit, full.
     */
    public boolean hasMore(PageRequest request) {
        if (next == null || items.isEmpty()) {
            return false;
        }
        return request == null || request.limit() == null || items.size() >= request.limit();
    }
}
