package io.opencode4j.event;

/**
 * An event together with the stream metadata the server sends around it.
 *
 * <p>OpenCode 2.2 does not use SSE {@code id:} lines; the event id is inside the payload. Events that change a
 * session's durable state also carry {@code durable.aggregateID} (the session id) and a per-aggregate,
 * monotonically increasing {@code durable.seq}, which lets a consumer detect missed events after a
 * reconnect. Transient events such as text deltas have no sequence.</p>
 *
 * @param id          server event id, for example {@code evt_...}, or {@code null}
 * @param created     server timestamp in epoch milliseconds, or {@code null}
 * @param directory   location directory the event belongs to, or {@code null} for server-wide events
 * @param aggregateId id of the aggregate (session) whose durable state changed, or {@code null}
 * @param seq         durable sequence number within the aggregate, or {@code null} for transient events
 * @param event       the decoded event
 */
public record EventEnvelope(
        String id,
        Long created,
        String directory,
        String aggregateId,
        Long seq,
        OpenCodeEvent event
) {

    /** The event's type, for example {@code "session.text.delta"}. */
    public String type() {
        return event.type();
    }

    /** Whether this event changed durable session state and carries a sequence number. */
    public boolean durable() {
        return seq != null;
    }
}
