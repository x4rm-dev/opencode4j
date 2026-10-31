package io.opencode4j.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import java.util.Map;

/**
 * Changes to apply to a session ({@code PATCH /api/session/{id}}). Fields left {@code null} are unchanged.
 *
 * @param title       new title
 * @param metadata    replacement metadata (not merged with the existing value)
 * @param permissions replacement session-level permission rules
 * @param archived    archive or unarchive the session
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SessionUpdate(
        String title,
        Map<String, Object> metadata,
        List<PermissionRule> permissions,
        Boolean archived
) {

    public static SessionUpdate title(String title) {
        return new SessionUpdate(title, null, null, null);
    }

    public static SessionUpdate metadata(Map<String, Object> metadata) {
        return new SessionUpdate(null, metadata, null, null);
    }

    public static SessionUpdate archived(boolean archived) {
        return new SessionUpdate(null, null, null, archived);
    }
}
