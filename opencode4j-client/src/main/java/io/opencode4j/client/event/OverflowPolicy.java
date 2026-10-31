package io.opencode4j.client.event;

/**
 * What a subscription does when its queue is full. Lifecycle events (tools, steps, executions, permissions)
 * are never dropped: if room cannot be made by shedding text or reasoning deltas, the queue is cleared and the
 * subscriber gets a {@link StreamGap.Reason#OVERFLOW} gap so it reconciles from history.
 */
public enum OverflowPolicy {
    /** Merge the incoming delta into the queued delta of the same message part; otherwise shed the oldest delta. */
    COALESCE_DELTAS,
    /** Shed the oldest queued delta. */
    DROP_OLDEST_DELTA,
    /**
     * Block the shared reader until there is room. Every other subscription stalls too and the server may drop
     * the connection, so use this in tests only.
     */
    BLOCK
}
