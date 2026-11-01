package io.opencode4j.client.event;

import io.opencode4j.event.OpenCodeEvent;
import io.opencode4j.event.SessionCreated;
import io.opencode4j.event.SessionDeleted;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Parent links between sessions, learned from {@code session.created} events, so that a subscriber to a root
 * session also receives the events of the sub-agent sessions it spawns.
 */
public final class SessionTree {

    /** Guards against parent cycles in malformed input. */
    private static final int MAX_DEPTH = 64;

    private final Map<String, String> parents = new ConcurrentHashMap<>();

    /** Updates the tree from one event. */
    public void observe(OpenCodeEvent event) {
        if (event instanceof SessionCreated created && created.sessionId() != null && created.parentId() != null) {
            parents.put(created.sessionId(), created.parentId());
        } else if (event instanceof SessionDeleted deleted && deleted.sessionId() != null) {
            parents.remove(deleted.sessionId());
        }
    }

    /** Records that {@code child} was spawned by {@code parent}. */
    public void register(String child, String parent) {
        parents.put(Objects.requireNonNull(child, "child"), Objects.requireNonNull(parent, "parent"));
    }

    /** The parent of {@code sessionId}, or {@code null} for a root or unknown session. */
    public String parentOf(String sessionId) {
        return sessionId == null ? null : parents.get(sessionId);
    }

    /** The topmost known ancestor of {@code sessionId}, or {@code sessionId} itself. */
    public String rootOf(String sessionId) {
        String current = sessionId;
        for (int depth = 0; current != null && depth < MAX_DEPTH; depth++) {
            String parent = parents.get(current);
            if (parent == null) {
                return current;
            }
            current = parent;
        }
        return current;
    }

    /** Whether {@code sessionId} is {@code root} or one of its descendants. */
    public boolean belongsTo(String sessionId, String root) {
        String current = sessionId;
        for (int depth = 0; current != null && depth < MAX_DEPTH; depth++) {
            if (current.equals(root)) {
                return true;
            }
            current = parents.get(current);
        }
        return false;
    }

    /** Number of known parent links. */
    public int size() {
        return parents.size();
    }
}
