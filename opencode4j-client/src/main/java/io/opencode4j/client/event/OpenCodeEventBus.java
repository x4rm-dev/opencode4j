package io.opencode4j.client.event;

import io.opencode4j.OpenCodeAuthException;
import io.opencode4j.OpenCodeException;
import io.opencode4j.client.OpenCodeTransport;
import io.opencode4j.client.SseStream;
import io.opencode4j.config.OpenCodeConfig;
import io.opencode4j.event.EventEnvelope;
import io.opencode4j.event.ServerConnected;
import io.opencode4j.event.SseEventParser;
import io.opencode4j.event.SseFrame;
import java.io.IOException;
import java.time.Duration;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;

/**
 * {@link EventBus} over {@code GET /api/event}.
 *
 * <p>One virtual reader thread owns the connection: it parses frames, updates the {@link SessionTree} and
 * hands each event to the matching subscriptions' queues. A watchdog closes the connection when nothing,
 * not even the server's 15-second {@code : heartbeat} comment, arrives within the heartbeat timeout. Dropped
 * connections are retried with exponential backoff and jitter; an authentication failure is fatal.</p>
 *
 * <p>Settings come from {@link OpenCodeConfig}: {@code sseReadTimeoutMs} (heartbeat timeout, {@code 0}
 * disables the watchdog), {@code sseReconnectBaseDelayMs}, {@code sseReconnectMaxDelayMs} and
 * {@code sseMaxRetries} (consecutive failed attempts before {@link ConnectionState#FAILED}; negative
 * retries forever).</p>
 */
@Slf4j
public final class OpenCodeEventBus implements EventBus {

    static final String EVENT_PATH = "/api/event";

    private final OpenCodeTransport transport;
    private final Duration heartbeatTimeout;
    private final long reconnectBaseMs;
    private final long reconnectMaxMs;
    private final int maxRetries;
    private final SessionTree tree = new SessionTree();
    private final Set<QueuedSubscription> subscriptions = ConcurrentHashMap.newKeySet();
    private final Object lifecycle = new Object();

    private volatile ConnectionState state = ConnectionState.IDLE;
    private volatile CompletableFuture<Void> live = new CompletableFuture<>();
    private volatile SseStream current;
    private Thread reader;
    private Thread watchdog;
    private boolean running;
    private boolean closed;

    public OpenCodeEventBus(OpenCodeTransport transport) {
        this.transport = Objects.requireNonNull(transport, "transport");
        OpenCodeConfig config = transport.config();
        this.heartbeatTimeout = Duration.ofMillis(Math.max(0, config.sseReadTimeoutMs()));
        this.reconnectBaseMs = Math.max(1, config.sseReconnectBaseDelayMs());
        this.reconnectMaxMs = Math.max(reconnectBaseMs, config.sseReconnectMaxDelayMs());
        this.maxRetries = config.sseMaxRetries();
    }

    @Override
    public Subscription subscribe(EventFilter filter, EventSink sink, SubscriptionOptions options) {
        QueuedSubscription subscription = new QueuedSubscription(filter, sink,
                options == null ? SubscriptionOptions.defaults() : options, this::unsubscribe);
        synchronized (lifecycle) {
            if (closed) {
                throw new IllegalStateException("Event bus is closed");
            }
            subscriptions.add(subscription);
            subscription.start();
            if (!running) {
                startReader();
            }
        }
        return subscription;
    }

    @Override
    public ConnectionState state() {
        return state;
    }

    @Override
    public CompletableFuture<Void> awaitLive() {
        return live;
    }

    @Override
    public SessionTree sessionTree() {
        return tree;
    }

    /** Number of open subscriptions. */
    public int subscriberCount() {
        return subscriptions.size();
    }

    @Override
    public void close() {
        synchronized (lifecycle) {
            if (closed) {
                return;
            }
            closed = true;
            stopReader();
            state = ConnectionState.CLOSED;
        }
        IllegalStateException cause = new IllegalStateException("Event bus closed");
        live.completeExceptionally(cause);
        for (QueuedSubscription subscription : subscriptions) {
            subscription.offerFailure(cause);
        }
    }

    private void unsubscribe(QueuedSubscription subscription) {
        synchronized (lifecycle) {
            subscriptions.remove(subscription);
            if (subscriptions.isEmpty() && running) {
                stopReader();
                if (!closed) {
                    state = ConnectionState.IDLE;
                }
            }
        }
    }

    /** Called with {@link #lifecycle} held. */
    private void startReader() {
        running = true;
        if (live.isDone()) {
            live = new CompletableFuture<>();
        }
        state = ConnectionState.CONNECTING;
        reader = Thread.ofVirtual().name("opencode-event-reader").start(this::readLoop);
        if (!heartbeatTimeout.isZero()) {
            watchdog = Thread.ofVirtual().name("opencode-event-watchdog").start(this::watchLoop);
        }
    }

    /** Called with {@link #lifecycle} held. */
    private void stopReader() {
        running = false;
        closeCurrent();
        if (reader != null) {
            reader.interrupt();
            reader = null;
        }
        if (watchdog != null) {
            watchdog.interrupt();
            watchdog = null;
        }
    }

    private boolean isRunning() {
        synchronized (lifecycle) {
            return running && Thread.currentThread() == reader;
        }
    }

    private void readLoop() {
        int failures = 0;
        boolean wasLive = false;
        long lostAt = System.currentTimeMillis();
        while (isRunning()) {
            Throwable cause;
            try (SseStream stream = transport.openStream(EVENT_PATH)) {
                if (!adopt(stream)) {
                    return;
                }
                SseFrame frame;
                while (isRunning() && (frame = stream.nextFrame()) != null) {
                    EventEnvelope envelope = SseEventParser.parseEnvelope(frame);
                    if (envelope == null) {
                        continue;
                    }
                    if (envelope.event() instanceof ServerConnected) {
                        failures = 0;
                        markLive();
                        if (wasLive) {
                            broadcastGap(new StreamGap(StreamGap.Reason.RECONNECT, lostAt, System.currentTimeMillis()));
                        }
                        wasLive = true;
                    }
                    tree.observe(envelope.event());
                    dispatch(envelope);
                }
                cause = new IOException("Event stream ended");
            } catch (OpenCodeAuthException e) {
                fail(e);
                return;
            } catch (IOException | RuntimeException e) {
                cause = e;
            } finally {
                release();
            }
            if (!isRunning()) {
                return;
            }
            if (state == ConnectionState.LIVE) {
                lostAt = System.currentTimeMillis();
            }
            failures++;
            if (maxRetries >= 0 && failures > maxRetries) {
                fail(new OpenCodeException("Event stream failed after " + (failures - 1) + " reconnect attempts: "
                        + cause.getMessage(), cause));
                return;
            }
            markReconnecting();
            long delay = backoffMillis(failures);
            log.warn("Event stream lost ({}); reconnect attempt {} in {} ms", cause.toString(), failures, delay);
            if (!sleep(delay)) {
                return;
            }
        }
    }

    /** Makes {@code stream} the watched connection unless this reader has been superseded. */
    private boolean adopt(SseStream stream) {
        synchronized (lifecycle) {
            if (!running || Thread.currentThread() != reader) {
                return false;
            }
            current = stream;
            return true;
        }
    }

    private void release() {
        synchronized (lifecycle) {
            if (Thread.currentThread() == reader) {
                current = null;
            }
        }
    }

    private void watchLoop() {
        long timeoutNanos = heartbeatTimeout.toNanos();
        long pollMillis = Math.max(10, heartbeatTimeout.toMillis() / 4);
        while (sleep(pollMillis)) {
            synchronized (lifecycle) {
                if (!running || Thread.currentThread() != watchdog) {
                    return;
                }
            }
            SseStream stream = current;
            if (stream != null && System.nanoTime() - stream.lastActivityNanos() > timeoutNanos) {
                log.warn("No event stream activity for {} ms; forcing a reconnect", heartbeatTimeout.toMillis());
                stream.close();
            }
        }
    }

    private void dispatch(EventEnvelope envelope) {
        for (QueuedSubscription subscription : subscriptions) {
            try {
                if (subscription.filter().accepts(envelope, tree)) {
                    subscription.offer(envelope);
                }
            } catch (RuntimeException e) {
                log.warn("Event filter threw for {}: {}", envelope.type(), e.toString());
            }
        }
    }

    private void broadcastGap(StreamGap gap) {
        for (QueuedSubscription subscription : subscriptions) {
            subscription.offerGap(gap);
        }
    }

    private void markLive() {
        state = ConnectionState.LIVE;
        live.complete(null);
    }

    private void markReconnecting() {
        synchronized (lifecycle) {
            if (!running) {
                return;
            }
            state = ConnectionState.RECONNECTING;
            if (live.isDone()) {
                live = new CompletableFuture<>();
            }
        }
    }

    private void fail(Throwable cause) {
        log.error("Event stream failed permanently: {}", cause.toString());
        synchronized (lifecycle) {
            running = false;
            reader = null;
            if (watchdog != null) {
                watchdog.interrupt();
                watchdog = null;
            }
            state = ConnectionState.FAILED;
            CompletableFuture<Void> pending = live;
            pending.completeExceptionally(cause);
            // The next subscription starts a fresh connection attempt.
            live = new CompletableFuture<>();
        }
        for (QueuedSubscription subscription : subscriptions) {
            subscription.offerFailure(cause);
        }
    }

    private void closeCurrent() {
        SseStream stream = current;
        if (stream != null) {
            stream.close();
        }
    }

    long backoffMillis(int attempt) {
        long exponential = reconnectBaseMs << Math.min(attempt - 1, 20);
        long capped = Math.min(reconnectMaxMs, exponential);
        // Full jitter over the upper half avoids reconnect storms while keeping a useful minimum delay.
        return capped / 2 + ThreadLocalRandom.current().nextLong(capped / 2 + 1);
    }

    private static boolean sleep(long millis) {
        try {
            TimeUnit.MILLISECONDS.sleep(millis);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
