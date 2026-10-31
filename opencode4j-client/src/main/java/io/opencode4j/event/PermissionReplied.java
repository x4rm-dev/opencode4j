package io.opencode4j.event;

/** A permission request was answered; {@code reply} is {@code once}, {@code always} or {@code reject}. */
public record PermissionReplied(
        String sessionId,
        String requestId,
        String reply
) implements OpenCodeEvent {

    @Override
    public String type() {
        return "permission.replied";
    }
}
