package io.opencode4j.model;

/**
 * Filters for listing sessions. Fields left {@code null} do not filter.
 *
 * @param directory only sessions in this location directory
 * @param parentId  only children of this session
 * @param rootsOnly only sessions without a parent (ignored when {@code parentId} is set)
 * @param archived  only archived ({@code true}) or unarchived ({@code false}) sessions
 * @param search    free-text match on the title
 */
public record SessionQuery(
        String directory,
        String parentId,
        boolean rootsOnly,
        Boolean archived,
        String search
) {

    /** Every session. */
    public static SessionQuery all() {
        return new SessionQuery(null, null, false, null, null);
    }

    /** Root sessions in {@code directory}. */
    public static SessionQuery roots(String directory) {
        return new SessionQuery(directory, null, true, null, null);
    }
}
