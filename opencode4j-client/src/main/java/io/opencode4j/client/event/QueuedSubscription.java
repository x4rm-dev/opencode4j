package io.opencode4j.client.event;

import io.opencode4j.event.EventEnvelope;
import io.opencode4j.event.OpenCodeEvent;
import io.opencode4j.event.ReasoningDelta;
import io.opencode4j.event.TextDelta;
import java.util.ArrayDeque;
import java.util.Iterator;
import java.util.Objects;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;

/**
 * A subscription backed by a bounded queue and its own virtual dispatcher thread.
 *
 * <p>The bus's reader thread only enqueues; the sink runs on the dispatcher. When the queue is full the
 * {@link OverflowPolicy} sheds text or reasoning deltas. Lifecycle events are never shed: if no delta can make
 * room, the queue is cleared and an {@link StreamGap.Reason#OVERFLOW} gap is queued so the sink reconciles.</p>
 */
@Slf4j
final class QueuedSubscription implements Subscription {

    private sealed interface Item permits Event, Gap, Failure {
    }

    private record Event(EventEnvelope envelope) implements Item {
    }

    private record Gap(StreamGap gap) implements Item {
    }

    private record Failure(Throwable cause) implements Item {
    }

    private final EventFilter filter;
    private final EventSink sink;
    private final SubscriptionOptions options;
    private final Consumer<QueuedSubscription> onClose;
    private final ArrayDeque<Item> queue = new ArrayDeque<>();
    private final ReentrantLock lock = new ReentrantLock();
    private final Condition notEmpty = lock.newCondition();
    private final Condition notFull = lock.newCondition();
    private final Thread dispatcher;

    private boolean closed;
    private boolean overflowed;
    private long dropped;

    QueuedSubscription(EventFilter filter, EventSink sink, SubscriptionOptions options,
                       Consumer<QueuedSubscription> onClose) {
        this.filter = Objects.requireNonNull(filter, "filter");
        this.sink = Objects.requireNonNull(sink, "sink");
        this.options = Objects.requireNonNull(options, "options");
        this.onClose = Objects.requireNonNull(onClose, "onClose");
        this.dispatcher = Thread.ofVirtual().name("opencode-events-" + options.name()).unstarted(this::dispatch);
    }

    void start() {
        dispatcher.start();
    }

    EventFilter filter() {
        return filter;
    }

    /** Enqueues an event from the reader thread. Never blocks unless the policy is {@link OverflowPolicy#BLOCK}. */
    void offer(EventEnvelope envelope) {
        lock.lock();
        try {
            if (closed) {
                return;
            }
            if (queue.size() < options.capacity()) {
                enqueue(new Event(envelope));
                return;
            }
            handleFull(envelope);
        } finally {
            lock.unlock();
        }
    }

    /** Enqueues a gap signal. Gaps bypass the capacity limit, since they replace what was lost. */
    void offerGap(StreamGap gap) {
        lock.lock();
        try {
            if (!closed) {
                enqueue(new Gap(gap));
            }
        } finally {
            lock.unlock();
        }
    }

    /** Enqueues a terminal failure; the dispatcher stops after delivering it. */
    void offerFailure(Throwable cause) {
        lock.lock();
        try {
            if (!closed) {
                enqueue(new Failure(cause));
            }
        } finally {
            lock.unlock();
        }
    }

    @Override
    public boolean overflowed() {
        lock.lock();
        try {
            return overflowed;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public long droppedEvents() {
        lock.lock();
        try {
            return dropped;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void close() {
        lock.lock();
        try {
            if (closed) {
                return;
            }
            closed = true;
            queue.clear();
            notEmpty.signalAll();
            notFull.signalAll();
        } finally {
            lock.unlock();
        }
        onClose.accept(this);
    }

    boolean isClosed() {
        lock.lock();
        try {
            return closed;
        } finally {
            lock.unlock();
        }
    }

    private void handleFull(EventEnvelope envelope) {
        switch (options.overflow()) {
            case BLOCK -> {
                while (!closed && queue.size() >= options.capacity()) {
                    notFull.awaitUninterruptibly();
                }
                if (!closed) {
                    enqueue(new Event(envelope));
                }
            }
            case COALESCE_DELTAS -> {
                if (!mergeIntoTail(envelope)) {
                    shedOrOverflow(envelope);
                }
            }
            case DROP_OLDEST_DELTA -> shedOrOverflow(envelope);
        }
    }

    private void shedOrOverflow(EventEnvelope envelope) {
        if (removeOldestDelta()) {
            dropped++;
            enqueue(new Event(envelope));
            return;
        }
        if (isDelta(envelope.event())) {
            // The queue holds only lifecycle events; dropping this delta loses nothing that history lacks.
            dropped++;
            return;
        }
        log.warn("Subscription '{}' fell behind with {} lifecycle events queued; discarding them and signalling a gap",
                options.name(), queue.size());
        dropped += queue.size();
        queue.clear();
        overflowed = true;
        long now = System.currentTimeMillis();
        enqueue(new Gap(new StreamGap(StreamGap.Reason.OVERFLOW, now, now)));
        enqueue(new Event(envelope));
    }

    private boolean mergeIntoTail(EventEnvelope incoming) {
        Item tail = queue.peekLast();
        if (!(tail instanceof Event(EventEnvelope queued))) {
            return false;
        }
        OpenCodeEvent merged = merge(queued.event(), incoming.event());
        if (merged == null) {
            return false;
        }
        queue.pollLast();
        queue.addLast(new Event(new EventEnvelope(queued.id(), queued.created(), queued.directory(),
                queued.aggregateId(), queued.seq(), merged)));
        return true;
    }

    private static OpenCodeEvent merge(OpenCodeEvent first, OpenCodeEvent second) {
        if (first instanceof TextDelta a && second instanceof TextDelta b && samePart(a.sessionId(), b.sessionId(),
                a.messageId(), b.messageId(), a.ordinal(), b.ordinal())) {
            return new TextDelta(a.sessionId(), a.messageId(), a.ordinal(), a.delta() + b.delta());
        }
        if (first instanceof ReasoningDelta a && second instanceof ReasoningDelta b && samePart(a.sessionId(),
                b.sessionId(), a.messageId(), b.messageId(), a.ordinal(), b.ordinal())) {
            return new ReasoningDelta(a.sessionId(), a.messageId(), a.ordinal(), a.delta() + b.delta());
        }
        return null;
    }

    private static boolean samePart(String sessionA, String sessionB, String messageA, String messageB,
                                    int ordinalA, int ordinalB) {
        return Objects.equals(sessionA, sessionB) && Objects.equals(messageA, messageB) && ordinalA == ordinalB;
    }

    private boolean removeOldestDelta() {
        Iterator<Item> items = queue.iterator();
        while (items.hasNext()) {
            if (items.next() instanceof Event(EventEnvelope envelope) && isDelta(envelope.event())) {
                items.remove();
                return true;
            }
        }
        return false;
    }

    private static boolean isDelta(OpenCodeEvent event) {
        return event instanceof TextDelta || event instanceof ReasoningDelta;
    }

    private void enqueue(Item item) {
        queue.addLast(item);
        notEmpty.signal();
    }

    private Item take() {
        lock.lock();
        try {
            while (!closed && queue.isEmpty()) {
                notEmpty.awaitUninterruptibly();
            }
            if (closed) {
                return null;
            }
            Item item = queue.pollFirst();
            notFull.signal();
            return item;
        } finally {
            lock.unlock();
        }
    }

    private void dispatch() {
        Item item;
        while ((item = take()) != null) {
            try {
                switch (item) {
                    case Event(EventEnvelope envelope) -> sink.onEvent(envelope);
                    case Gap(StreamGap gap) -> sink.onGap(gap);
                    case Failure(Throwable cause) -> {
                        sink.onFailure(cause);
                        close();
                        return;
                    }
                }
            } catch (Throwable failure) {
                // The dispatcher must outlive a faulty sink, or the subscription would silently stop.
                log.warn("Event sink of subscription '{}' threw: {}", options.name(), failure.toString(), failure);
            }
        }
    }
}
