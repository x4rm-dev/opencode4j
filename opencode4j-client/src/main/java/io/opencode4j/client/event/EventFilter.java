package io.opencode4j.client.event;

import io.opencode4j.event.EventEnvelope;
import java.util.Objects;
import java.util.Set;

/** Selects the events one subscription receives. */
@FunctionalInterface
public interface EventFilter {

    /** Whether {@code event} should be delivered, given the bus's current session tree. */
    boolean accepts(EventEnvelope event, SessionTree tree);

    /** Every event. */
    static EventFilter all() {
        return (event, tree) -> true;
    }

    /**
     * Events of {@code rootSessionId} and every session it spawned, at any depth. Child sessions are learned
     * from {@code session.created} before the event is filtered, so a child's own creation event is included.
     */
    static EventFilter sessionTree(String rootSessionId) {
        Objects.requireNonNull(rootSessionId, "rootSessionId");
        return (event, tree) -> tree.belongsTo(event.event().sessionId(), rootSessionId);
    }

    /** Events of the given types, for example {@code "mcp.status.changed"}. */
    static EventFilter types(Set<String> types) {
        Set<String> wanted = Set.copyOf(types);
        return (event, tree) -> wanted.contains(event.type());
    }

    /** Events that both filters accept. */
    default EventFilter and(EventFilter other) {
        Objects.requireNonNull(other, "other");
        return (event, tree) -> accepts(event, tree) && other.accepts(event, tree);
    }
}
