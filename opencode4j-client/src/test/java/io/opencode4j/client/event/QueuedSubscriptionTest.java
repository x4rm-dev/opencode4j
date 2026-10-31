package io.opencode4j.client.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.opencode4j.event.EventEnvelope;
import io.opencode4j.event.ExecutionEnded;
import io.opencode4j.event.ExecutionOutcome;
import io.opencode4j.event.ReasoningDelta;
import io.opencode4j.event.StepEnded;
import io.opencode4j.event.TextDelta;
import io.opencode4j.model.TokenUsage;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

/**
 * Overflow behaviour, driven with a sink that blocks until released so the queue fills deterministically.
 */
class QueuedSubscriptionTest {

    private final List<String> delivered = new CopyOnWriteArrayList<>();
    private final CountDownLatch release = new CountDownLatch(1);
    private final CountDownLatch firstTaken = new CountDownLatch(1);

    @Test
    void coalescesDeltasOfTheSamePartWhenFull() throws Exception {
        // Given
        QueuedSubscription subscription = blocked(2, OverflowPolicy.COALESCE_DELTAS);

        // When: one event held by the sink, two queued, then three more deltas of the tail's part
        subscription.offer(step("s"));
        assertTrue(firstTaken.await(2, TimeUnit.SECONDS));
        subscription.offer(text("s", "m", "a"));
        subscription.offer(text("s", "m", "b"));
        subscription.offer(text("s", "m", "c"));
        subscription.offer(text("s", "m", "d"));
        releaseAndDrain(subscription, 3);

        // Then
        assertEquals(List.of("step", "a", "bcd"), delivered);
        assertEquals(0, subscription.droppedEvents());
        assertFalse(subscription.overflowed());
    }

    @Test
    void shedsTheOldestDeltaForALifecycleEvent() throws Exception {
        // Given
        QueuedSubscription subscription = blocked(2, OverflowPolicy.DROP_OLDEST_DELTA);
        subscription.offer(step("s"));
        assertTrue(firstTaken.await(2, TimeUnit.SECONDS));

        // When
        subscription.offer(text("s", "m", "a"));
        subscription.offer(reasoning("s", "think"));
        subscription.offer(ended("s"));
        releaseAndDrain(subscription, 3);

        // Then
        assertEquals(List.of("step", "think", "ended"), delivered);
        assertEquals(1, subscription.droppedEvents());
    }

    @Test
    void dropsAnIncomingDeltaWhenOnlyLifecycleEventsAreQueued() throws Exception {
        // Given
        QueuedSubscription subscription = blocked(1, OverflowPolicy.COALESCE_DELTAS);
        subscription.offer(step("s"));
        assertTrue(firstTaken.await(2, TimeUnit.SECONDS));

        // When
        subscription.offer(ended("s"));
        subscription.offer(text("s", "m", "late"));
        releaseAndDrain(subscription, 2);

        // Then
        assertEquals(List.of("step", "ended"), delivered);
        assertEquals(1, subscription.droppedEvents());
        assertFalse(subscription.overflowed());
    }

    @Test
    void clearsTheQueueAndSignalsAGapWhenLifecycleEventsOverflow() throws Exception {
        // Given
        QueuedSubscription subscription = blocked(2, OverflowPolicy.COALESCE_DELTAS);
        subscription.offer(step("s"));
        assertTrue(firstTaken.await(2, TimeUnit.SECONDS));

        // When
        subscription.offer(step("s"));
        subscription.offer(step("s"));
        subscription.offer(ended("s"));
        releaseAndDrain(subscription, 3);

        // Then
        assertEquals(List.of("step", "gap:OVERFLOW", "ended"), delivered);
        assertTrue(subscription.overflowed());
        assertEquals(2, subscription.droppedEvents());
    }

    @Test
    void closingStopsDelivery() throws Exception {
        // Given
        QueuedSubscription subscription = blocked(4, OverflowPolicy.COALESCE_DELTAS);
        subscription.offer(step("s"));
        assertTrue(firstTaken.await(2, TimeUnit.SECONDS));
        subscription.offer(ended("s"));

        // When
        subscription.close();
        release.countDown();
        Thread.sleep(100);

        // Then
        assertEquals(List.of("step"), delivered);
        assertTrue(subscription.isClosed());
    }

    private QueuedSubscription blocked(int capacity, OverflowPolicy policy) {
        QueuedSubscription subscription = new QueuedSubscription(EventFilter.all(), new EventSink() {
            @Override
            public void onEvent(EventEnvelope event) {
                record(switch (event.event()) {
                    case TextDelta delta -> delta.delta();
                    case ReasoningDelta delta -> delta.delta();
                    case StepEnded step -> "step";
                    case ExecutionEnded end -> "ended";
                    default -> event.type();
                });
            }

            @Override
            public void onGap(StreamGap gap) {
                record("gap:" + gap.reason());
            }
        }, new SubscriptionOptions(capacity, policy, "test"), closed -> { });
        subscription.start();
        return subscription;
    }

    private void record(String value) {
        delivered.add(value);
        if (firstTaken.getCount() > 0) {
            firstTaken.countDown();
            try {
                release.await(2, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private void releaseAndDrain(QueuedSubscription subscription, int expected) throws InterruptedException {
        release.countDown();
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while (delivered.size() < expected && System.nanoTime() < deadline) {
            Thread.sleep(10);
        }
        Thread.sleep(50);
        subscription.close();
    }

    private static EventEnvelope envelope(io.opencode4j.event.OpenCodeEvent event) {
        return new EventEnvelope(null, null, null, null, null, event);
    }

    private static EventEnvelope text(String session, String message, String delta) {
        return envelope(new TextDelta(session, message, 0, delta));
    }

    private static EventEnvelope reasoning(String session, String delta) {
        return envelope(new ReasoningDelta(session, "m", 0, delta));
    }

    private static EventEnvelope step(String session) {
        return envelope(new StepEnded(session, "m", "stop", TokenUsage.ZERO));
    }

    private static EventEnvelope ended(String session) {
        return envelope(new ExecutionEnded(session, ExecutionOutcome.SUCCEEDED, null));
    }
}
