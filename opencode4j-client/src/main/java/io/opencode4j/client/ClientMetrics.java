package io.opencode4j.client;

import io.opencode4j.client.event.ConnectionState;
import io.opencode4j.client.event.StreamGap;

/**
 * Receives client measurements. Bind it to Micrometer, Dropwizard Metrics or a log; every method has a no-op
 * default and must return quickly, because it runs on request and event-stream threads.
 */
public interface ClientMetrics {

    /**
     * One HTTP request finished.
     *
     * @param route  the path with ids replaced by {@code {id}} and without the query, for example
     *               {@code /api/session/{id}/message}
     * @param status the HTTP status, or {@code -1} if no response arrived
     */
    default void httpRequest(String method, String route, int status, long durationNanos) {
    }

    /** The shared event stream changed state. */
    default void streamStateChanged(ConnectionState state) {
    }

    /** Subscribers may have missed events. */
    default void streamGap(StreamGap.Reason reason) {
    }

    /** An event type this library does not model arrived; a rising count signals protocol drift. */
    default void unknownEvent(String type) {
    }

    /** A subscriber fell behind and events were shed or discarded. */
    default void eventsDropped(String subscription, long count) {
    }

    static ClientMetrics noop() {
        return new ClientMetrics() { };
    }
}
