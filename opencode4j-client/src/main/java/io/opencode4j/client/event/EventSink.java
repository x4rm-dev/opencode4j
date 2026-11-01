package io.opencode4j.client.event;

import io.opencode4j.event.EventEnvelope;

/**
 * Receives one subscription's events, in order, on that subscription's own dispatcher thread.
 *
 * <p>A slow sink delays only its own subscription. Exceptions thrown by a sink are logged and do not stop
 * delivery.</p>
 */
@FunctionalInterface
public interface EventSink {

    /** One event that matched the subscription's filter. */
    void onEvent(EventEnvelope event);

    /** Events may have been missed; reconcile from history. */
    default void onGap(StreamGap gap) {
    }

    /**
     * The stream failed for good (reconnects exhausted, credentials rejected) or the bus was closed. No more
     * callbacks follow.
     */
    default void onFailure(Throwable cause) {
    }
}
