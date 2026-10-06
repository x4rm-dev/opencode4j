package io.opencode4j.event;

import java.util.List;

/**
 * The agent asks permission to perform an action. Answer it with {@code InteractionApi.replyPermission}.
 *
 * <p>The payload layout is taken from the server's {@code Permission.Request} schema; unlike the other event
 * types it has not been observed on a live stream.</p>
 */
public record PermissionAsked(
        String id,
        String sessionId,
        String action,
        List<String> resources,
        String message
) implements OpenCodeEvent {

    public PermissionAsked {
        resources = resources == null ? List.of() : List.copyOf(resources);
    }

    @Override
    public String type() {
        return "permission.asked";
    }
}
