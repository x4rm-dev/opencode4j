package io.opencode4j.client.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.opencode4j.OpenCodeAuthException;
import io.opencode4j.client.OpenCodeClient;
import io.opencode4j.config.OpenCodeConfig;
import io.opencode4j.event.EventEnvelope;
import io.opencode4j.event.TextDelta;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class OpenCodeEventBusTest {

    private static final String CONNECTED = frame("server.connected", "");

    private MockWebServer server;
    private OpenCodeClient client;
    private final Queue<MockResponse> streams = new ConcurrentLinkedQueue<>();
    private final AtomicInteger streamRequests = new AtomicInteger();

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.setDispatcher(new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) {
                if (request.getPath() != null && request.getPath().startsWith("/api/event")) {
                    streamRequests.incrementAndGet();
                    MockResponse next = streams.poll();
                    return next != null ? next : stalled(CONNECTED);
                }
                return new MockResponse().setResponseCode(404);
            }
        });
        server.start();
    }

    @AfterEach
    void tearDown() throws IOException {
        if (client != null) {
            client.close();
        }
        server.shutdown();
    }

    @Test
    void sharesOneConnectionAndRoutesBySessionTree() throws Exception {
        // Given: a child created after subscribing, a foreign session, and events without a location
        streams.add(stalled(CONNECTED
                + frame("session.created", "\"sessionID\":\"child\",\"parentID\":\"root\"")
                + frame("session.created", "\"sessionID\":\"grandchild\",\"parentID\":\"child\"")
                + text("root", "a") + text("child", "b") + text("grandchild", "c") + text("foreign", "x")
                + frame("session.execution.succeeded", "\"sessionID\":\"root\"")));
        EventBus bus = bus(config());
        List<EventEnvelope> rootEvents = new CopyOnWriteArrayList<>();
        List<EventEnvelope> foreignEvents = new CopyOnWriteArrayList<>();

        // When
        Subscription root = bus.subscribe(EventFilter.sessionTree("root"), rootEvents::add);
        Subscription foreign = bus.subscribe(EventFilter.sessionTree("foreign"), foreignEvents::add);
        bus.awaitLive().get(3, TimeUnit.SECONDS);
        await(() -> rootEvents.size() == 6 && foreignEvents.size() == 1);

        // Then
        assertEquals(1, streamRequests.get());
        assertEquals(List.of("session.created", "session.created", "session.text.delta", "session.text.delta",
                "session.text.delta", "session.execution.succeeded"),
                rootEvents.stream().map(EventEnvelope::type).toList());
        assertEquals("x", ((TextDelta) foreignEvents.getFirst().event()).delta());
        assertEquals("root", bus.sessionTree().rootOf("grandchild"));
        root.close();
        foreign.close();
    }

    @Test
    void reconnectsAfterEofAndSignalsAGapBeforeNewEvents() throws Exception {
        // Given
        streams.add(new MockResponse().setHeader("Content-Type", "text/event-stream")
                .setBody(CONNECTED + text("root", "before")));
        streams.add(stalled(CONNECTED + text("root", "after")));
        EventBus bus = bus(config());
        List<String> seen = new CopyOnWriteArrayList<>();

        // When
        bus.subscribe(EventFilter.sessionTree("root"), new EventSink() {
            @Override
            public void onEvent(EventEnvelope event) {
                seen.add(((TextDelta) event.event()).delta());
            }

            @Override
            public void onGap(StreamGap gap) {
                seen.add("gap:" + gap.reason());
            }
        });
        await(() -> seen.size() == 3);

        // Then
        assertEquals(List.of("before", "gap:RECONNECT", "after"), seen);
        assertEquals(2, streamRequests.get());
        assertEquals(ConnectionState.LIVE, bus.state());
    }

    @Test
    void heartbeatWatchdogReplacesASilentConnection() throws Exception {
        // Given: the first connection goes silent after server.connected; heartbeats keep the second alive
        streams.add(stalled(CONNECTED));
        EventBus bus = bus(config().sseReadTimeoutMs(300));
        List<StreamGap> gaps = new CopyOnWriteArrayList<>();

        // When
        bus.subscribe(EventFilter.all(), new EventSink() {
            @Override
            public void onEvent(EventEnvelope event) {
            }

            @Override
            public void onGap(StreamGap gap) {
                gaps.add(gap);
            }
        });
        await(() -> !gaps.isEmpty());

        // Then
        assertEquals(StreamGap.Reason.RECONNECT, gaps.getFirst().reason());
        assertTrue(streamRequests.get() >= 2);
    }

    @Test
    void failsAfterReconnectAttemptsAreExhausted() throws Exception {
        // Given
        for (int i = 0; i < 5; i++) {
            streams.add(new MockResponse().setResponseCode(503));
        }
        EventBus bus = bus(config().sseMaxRetries(2));
        List<Throwable> failures = new CopyOnWriteArrayList<>();

        // When
        bus.subscribe(EventFilter.all(), new EventSink() {
            @Override
            public void onEvent(EventEnvelope event) {
            }

            @Override
            public void onFailure(Throwable cause) {
                failures.add(cause);
            }
        });
        ExecutionException failure = assertThrows(ExecutionException.class,
                () -> bus.awaitLive().get(3, TimeUnit.SECONDS));

        // Then
        await(() -> failures.size() == 1);
        assertEquals(3, streamRequests.get(), "one attempt plus two retries");
        assertEquals(ConnectionState.FAILED, bus.state());
        assertTrue(failure.getCause().getMessage().contains("2 reconnect attempts"), failure.getCause().getMessage());
    }

    @Test
    void rejectedCredentialsFailImmediately() throws Exception {
        // Given
        streams.add(new MockResponse().setResponseCode(401).setBody("{\"_tag\":\"UnauthorizedError\"}"));
        EventBus bus = bus(config());
        List<Throwable> failures = new CopyOnWriteArrayList<>();

        // When
        bus.subscribe(EventFilter.all(), new EventSink() {
            @Override
            public void onEvent(EventEnvelope event) {
            }

            @Override
            public void onFailure(Throwable cause) {
                failures.add(cause);
            }
        });
        await(() -> failures.size() == 1);

        // Then
        assertInstanceOf(OpenCodeAuthException.class, failures.getFirst());
        assertEquals(1, streamRequests.get());
    }

    @Test
    void aSlowSubscriberDoesNotDelayOthers() throws Exception {
        // Given
        StringBuilder body = new StringBuilder(CONNECTED);
        for (int i = 0; i < 50; i++) {
            body.append(text("root", "t" + i));
        }
        streams.add(stalled(body.toString()));
        EventBus bus = bus(config());
        AtomicInteger fast = new AtomicInteger();
        AtomicInteger slow = new AtomicInteger();

        // When
        bus.subscribe(EventFilter.sessionTree("root"), event -> {
            slow.incrementAndGet();
            sleep(200);
        });
        bus.subscribe(EventFilter.sessionTree("root"), event -> fast.incrementAndGet());
        await(() -> fast.get() == 50);

        // Then
        assertTrue(slow.get() < 50, "the slow subscriber is still working through its queue");
    }

    @Test
    void closingTheLastSubscriptionDisconnectsAndANewOneReconnects() throws Exception {
        // Given
        EventBus bus = bus(config());
        Subscription first = bus.subscribe(EventFilter.all(), event -> { });
        bus.awaitLive().get(3, TimeUnit.SECONDS);

        // When
        first.close();

        // Then
        assertEquals(ConnectionState.IDLE, bus.state());
        bus.subscribe(EventFilter.all(), event -> { });
        bus.awaitLive().get(3, TimeUnit.SECONDS);
        assertEquals(2, streamRequests.get());
    }

    @Test
    void clientCloseFailsSubscribersAndRejectsNewOnes() throws Exception {
        // Given
        EventBus bus = bus(config());
        List<Throwable> failures = new CopyOnWriteArrayList<>();
        bus.subscribe(EventFilter.all(), new EventSink() {
            @Override
            public void onEvent(EventEnvelope event) {
            }

            @Override
            public void onFailure(Throwable cause) {
                failures.add(cause);
            }
        });
        bus.awaitLive().get(3, TimeUnit.SECONDS);

        // When
        client.close();

        // Then
        await(() -> failures.size() == 1);
        assertEquals(ConnectionState.CLOSED, bus.state());
        assertThrows(IllegalStateException.class, () -> client.eventBus());
        assertThrows(IllegalStateException.class, () -> bus.subscribe(EventFilter.all(), event -> { }));
    }

    @Test
    void backoffGrowsExponentiallyWithJitterAndIsCapped() {
        OpenCodeEventBus bus = new OpenCodeEventBus(OpenCodeClient.create(
                config().sseReconnectBaseDelayMs(100).sseReconnectMaxDelayMs(1_000).build()).transport());
        List<Long> delays = new ArrayList<>();
        for (int attempt = 1; attempt <= 6; attempt++) {
            delays.add(bus.backoffMillis(attempt));
        }
        assertTrue(delays.get(0) >= 50 && delays.get(0) <= 100, delays.toString());
        assertTrue(delays.get(2) >= 200 && delays.get(2) <= 400, delays.toString());
        assertTrue(delays.get(5) >= 500 && delays.get(5) <= 1_000, delays.toString());
    }

    @Test
    void theClientReturnsOneSharedBus() {
        client = OpenCodeClient.create(config().build());
        assertSame(client.eventBus(), client.eventBus());
    }

    private EventBus bus(OpenCodeConfig.Builder config) {
        client = OpenCodeClient.create(config.build());
        return client.eventBus();
    }

    private OpenCodeConfig.Builder config() {
        return OpenCodeConfig.builder(server.url("/").toString())
                .connectTimeoutMs(2_000)
                .sseReconnectBaseDelayMs(20)
                .sseReconnectMaxDelayMs(50)
                .sseMaxRetries(20);
    }

    private static MockResponse stalled(String body) {
        // A Content-Length beyond the body keeps the connection open after the events, like a live stream.
        // setBody resets Content-Length, so the header must be set after it.
        return new MockResponse().setHeader("Content-Type", "text/event-stream")
                .setBody(body)
                .setHeader("Content-Length", body.length() + 100_000);
    }

    private static String frame(String type, String data) {
        return "data: {\"id\":\"evt\",\"type\":\"" + type + "\",\"data\":{" + data + "}}\n\n";
    }

    private static String text(String sessionId, String delta) {
        return frame("session.text.delta", "\"sessionID\":\"" + sessionId + "\",\"assistantMessageID\":\"m\","
                + "\"ordinal\":0,\"delta\":\"" + delta + "\"");
    }

    private static void await(BooleanSupplier condition) throws InterruptedException {
        long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
        while (!condition.getAsBoolean()) {
            if (System.nanoTime() > deadline) {
                throw new AssertionError("condition not met within 5 s");
            }
            Thread.sleep(10);
        }
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
