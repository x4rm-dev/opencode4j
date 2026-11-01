package io.opencode4j.client.event;

/** A live registration on an {@link EventBus}. Closing it stops delivery; queued events are discarded. */
public interface Subscription extends AutoCloseable {

    /** Whether the queue has overflowed at least once, discarding events. */
    boolean overflowed();

    /** Events shed or discarded because the subscriber fell behind. */
    long droppedEvents();

    @Override
    void close();
}
