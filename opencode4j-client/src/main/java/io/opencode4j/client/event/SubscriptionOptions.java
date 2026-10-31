package io.opencode4j.client.event;

import java.util.Objects;

/**
 * Per-subscription delivery settings.
 *
 * @param capacity queued events before {@code overflow} applies
 * @param overflow what to do when the queue is full
 * @param name     label for the dispatcher thread and logs
 */
public record SubscriptionOptions(
        int capacity,
        OverflowPolicy overflow,
        String name
) {

    public static final int DEFAULT_CAPACITY = 1_024;

    public SubscriptionOptions {
        if (capacity <= 0) {
            throw new IllegalArgumentException("capacity must be positive: " + capacity);
        }
        Objects.requireNonNull(overflow, "overflow");
        name = name == null || name.isBlank() ? "subscription" : name;
    }

    public static SubscriptionOptions defaults() {
        return new SubscriptionOptions(DEFAULT_CAPACITY, OverflowPolicy.COALESCE_DELTAS, null);
    }

    public SubscriptionOptions named(String name) {
        return new SubscriptionOptions(capacity, overflow, name);
    }
}
