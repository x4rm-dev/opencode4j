package io.opencode4j.client.event;

import java.util.concurrent.CompletableFuture;

/**
 * One shared, self-healing connection to the server's event stream, fanned out to many subscribers.
 *
 * <p>The connection opens with the first subscription and closes with the last. Each subscription has its own
 * bounded queue and dispatcher thread, so a slow subscriber never stalls the reader or other subscribers.
 * When the connection drops, the bus reconnects with exponential backoff and signals a {@link StreamGap} to
 * every subscriber, because the server does not replay missed events.</p>
 */
public interface EventBus extends AutoCloseable {

    /** Registers a subscriber and connects if this is the first one. */
    Subscription subscribe(EventFilter filter, EventSink sink, SubscriptionOptions options);

    /** Registers a subscriber with {@link SubscriptionOptions#defaults()}. */
    default Subscription subscribe(EventFilter filter, EventSink sink) {
        return subscribe(filter, sink, SubscriptionOptions.defaults());
    }

    /** The connection's current state. */
    ConnectionState state();

    /**
     * Completes when the stream is live (the server sent {@code server.connected}), immediately if it already
     * is. Completes exceptionally if the bus fails. Subscribe first: an idle bus does not connect.
     */
    CompletableFuture<Void> awaitLive();

    /** Parent links learned from the stream. */
    SessionTree sessionTree();

    /** Closes the connection and every subscription. */
    @Override
    void close();
}
