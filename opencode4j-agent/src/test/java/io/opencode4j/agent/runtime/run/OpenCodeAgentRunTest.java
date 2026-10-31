package io.opencode4j.agent.runtime.run;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.opencode4j.client.OpenCodeClient;
import io.opencode4j.config.OpenCodeConfig;
import io.opencode4j.runtime.AgentEventListener;
import io.opencode4j.runtime.FailureCategory;
import io.opencode4j.runtime.RunStatus;
import io.opencode4j.runtime.RunPrompt;
import io.opencode4j.runtime.event.AgentEvent;
import io.opencode4j.runtime.event.DelegationCompletedEvent;
import io.opencode4j.runtime.event.DelegationStartedEvent;
import io.opencode4j.runtime.event.RunCompletedEvent;
import io.opencode4j.runtime.event.RunFailedEvent;
import io.opencode4j.runtime.event.RunInterruptedEvent;
import io.opencode4j.runtime.event.StepEndedEvent;
import io.opencode4j.runtime.event.TextDeltaEvent;
import io.opencode4j.runtime.spec.AgentSpec;
import io.opencode4j.runtime.spec.RunLimits;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.IntStream;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class OpenCodeAgentRunTest {
    private MockWebServer server;
    private OpenCodeClient client;
    private final List<AgentEvent> events = new CopyOnWriteArrayList<>();

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
        client = OpenCodeClient.create(OpenCodeConfig.builder(server.url("/").toString())
                .connectTimeoutMs(2000).sseReconnectBaseDelayMs(20).sseReconnectMaxDelayMs(50).build());
    }

    @AfterEach
    void tearDown() throws IOException {
        client.close();
        server.shutdown();
    }

    @Test
    void accountsOnlyRootAndRegisteredDescendantsAndCountsRootSteps() throws Exception {
        String stream = event("session.created", "\"sessionID\":\"foreign\",\"parentID\":\"other\",\"title\":\"stranger\"")
                + event("session.created", "\"sessionID\":\"foreign-child\",\"parentID\":\"foreign\"")
                + event("session.created", "\"sessionID\":\"child\",\"parentID\":\"root\",\"title\":\"worker\"")
                + event("session.created", "\"sessionID\":\"grandchild\",\"parentID\":\"child\",\"title\":\"nested\"")
                + event("session.created", "\"sessionID\":\"root\",\"parentID\":\"foreign\",\"title\":\"wrong\"")
                + text("foreign", "ignored") + text("foreign-child", "ignored") + text("unknown", "ignored")
                + step("foreign", 999, 10) + step("foreign-child", 999, 10) + step("unknown", 999, 10)
                + text("child", "child text") + step("child", 3, 0.03)
                + text("grandchild", "nested text") + step("grandchild", 4, 0.04)
                + event("session.execution.succeeded", "\"sessionID\":\"child\"")
                + event("session.step.failed", "\"sessionID\":\"root\",\"assistantMessageID\":\"failed\","
                        + "\"error\":{\"type\":\"aborted\"},\"tokens\":{\"input\":5,\"output\":1}")
                + text("root", "root text") + step("root", 6, 0.06)
                + event("session.execution.succeeded", "\"sessionID\":\"root\"")
                + event("session.execution.failed", "\"sessionID\":\"root\",\"message\":\"duplicate\"");
        configure(stream, false, false);
        var run = run(AgentSpec.builder("boss").steps(2).build(), events::add);
        run.start();
        var result = run.result().get(3, TimeUnit.SECONDS);

        assertTrue(result.isSuccess());
        assertTrue(result.truncated());
        assertEquals("root text", result.text());
        assertEquals(19, result.tokenUsage().total());
        assertEquals(0.13, result.totalCost(), 0.000001);
        assertEquals(List.of("boss/worker", "boss/worker/nested", "boss", "boss"),
                result.steps().stream().map(s -> s.agentPath()).toList());
        assertEquals(List.of(1, 2, 3, 4), result.steps().stream().map(s -> s.ordinal()).toList());
        assertEquals(3, events.stream().filter(TextDeltaEvent.class::isInstance).count());
        assertEquals(1, terminalCount());
        assertTrue(events.stream().anyMatch(RunCompletedEvent.class::isInstance));
        run.cancel();
        assertEquals(1, terminalCount());
    }

    @Test
    void anErrorInTheRunLoopFailsTheRunInsteadOfLeavingAwaitBlockedForever() throws Exception {
        configure(text("root", "hello") + event("session.execution.succeeded", "\"sessionID\":\"root\""),
                false, false);
        AgentEventListener linkageFailure = e -> {
            if (e instanceof TextDeltaEvent) {
                throw new NoSuchMethodError("simulated dependency clash");
            }
        };
        var run = run(AgentSpec.builder("boss").build(), linkageFailure);

        run.start();
        var result = run.result().get(3, TimeUnit.SECONDS);

        assertFalse(result.isSuccess());
        assertTrue(result.errorMessage().contains("simulated dependency clash"));
    }

    @Test
    void childStepsDoNotTruncateRootAndToolIdsAreScopedToSessions() throws Exception {
        String stream = event("session.created", "\"sessionID\":\"child\",\"parentID\":\"root\",\"title\":\"worker\"")
                + toolStarted("root", "same", "execute")
                + toolCalled("root", "same", "\"code\":\"tools.vault.read({})\"")
                + toolStarted("child", "same", "subagent")
                + toolCalled("child", "same", "\"agent\":\"nested\",\"description\":\"work\",\"prompt\":\"do it\"")
                + event("session.tool.failed", "\"sessionID\":\"child\",\"id\":\"same\",\"error\":\"failed child\"")
                + event("session.tool.success", "\"sessionID\":\"root\",\"id\":\"same\","
                        + "\"content\":[{\"type\":\"text\",\"text\":\"secret\"}]")
                + step("child", 3, 0.03) + step("child", 4, 0.04)
                + step("root", 6, 0.06)
                + event("session.execution.succeeded", "\"sessionID\":\"root\"");
        configure(stream, false, false);
        var run = run(AgentSpec.builder("boss").steps(2).build(), events::add);
        run.start();
        var result = run.result().get(3, TimeUnit.SECONDS);

        assertFalse(result.truncated());
        assertEquals(2, result.toolExecutions().size());
        assertEquals("subagent", result.toolExecutions().get(0).toolName());
        assertTrue(result.toolExecutions().get(0).isError());
        assertEquals("vault.read", result.toolExecutions().get(1).toolName());
        assertEquals("secret", result.toolExecutions().get(1).result());
        assertTrue(events.stream().anyMatch(e -> e instanceof DelegationStartedEvent d
                && d.targetAgentName().equals("nested")));
        assertTrue(events.stream().anyMatch(e -> e instanceof DelegationCompletedEvent));
    }

    @Test
    void reconcilesFromHistoryWhenTheStreamDropsMidRun() throws Exception {
        // Given: the stream drops after one step; history shows the turn finished meanwhile
        configure(text("root", "partial") + step("root", 2, 0.02), false, false);
        active = "{\"data\":{}}";
        history = "{\"data\":["
                + "{\"id\":\"msg_3\",\"type\":\"idle\",\"time\":{\"created\":3},\"outcome\":\"succeeded\"},"
                + "{\"id\":\"msg_2\",\"type\":\"assistant\",\"time\":{\"created\":2},\"agent\":\"boss\","
                + "\"model\":{\"id\":\"m\",\"providerID\":\"p\"},"
                + "\"content\":[{\"type\":\"text\",\"text\":\"final answer\"}]},"
                + "{\"id\":\"msg_0\",\"type\":\"assistant\",\"time\":{\"created\":0},\"agent\":\"boss\","
                + "\"model\":{\"id\":\"m\",\"providerID\":\"p\"},"
                + "\"content\":[{\"type\":\"text\",\"text\":\"previous turn\"}]}],\"cursor\":{}}";
        var run = run(AgentSpec.builder("boss").build(), events::add);

        // When
        run.start();
        var result = run.result().get(3, TimeUnit.SECONDS);

        // Then
        assertTrue(result.isSuccess(), result.errorMessage());
        assertEquals("final answer", result.text());
        assertEquals(2, result.tokenUsage().total(), "accounting streamed before the gap is kept");
        assertEquals(1, terminalCount());
    }

    @Test
    void keepsListeningWhenTheSessionIsStillRunningAfterAGap() throws Exception {
        // Given: the session is still active when the gap is reconciled; the end arrives on the new connection
        configure(text("root", "partial"), false, false);
        active = "{\"data\":{\"root\":{\"type\":\"running\"}}}";
        secondStream = text("root", " more") + event("session.execution.succeeded", "\"sessionID\":\"root\"");
        var run = run(AgentSpec.builder("boss").build(), events::add);

        // When
        run.start();
        var result = run.result().get(3, TimeUnit.SECONDS);

        // Then
        assertTrue(result.isSuccess(), result.errorMessage());
        assertEquals("partial more", result.text());
    }

    @Test
    void failsWithPartialAccountingWhenTheStreamCannotBeRecovered() throws Exception {
        // Given: the stream drops for good and history cannot be read
        client.close();
        client = OpenCodeClient.create(OpenCodeConfig.builder(server.url("/").toString())
                .connectTimeoutMs(2000).sseReconnectBaseDelayMs(20).sseReconnectMaxDelayMs(50)
                .sseMaxRetries(1).build());
        configure(text("root", "partial") + step("root", 2, 0.02), false, false);
        secondStream = null;
        var run = run(AgentSpec.builder("boss").build(), events::add);

        // When
        run.start();
        var result = run.result().get(3, TimeUnit.SECONDS);

        // Then
        assertFalse(result.isSuccess());
        assertEquals("partial", result.text());
        assertEquals(2, result.tokenUsage().total());
        assertTrue(result.errorMessage().contains("Event stream failed"), result.errorMessage());
        assertTrue(events.stream().anyMatch(RunFailedEvent.class::isInstance));
        assertEquals(1, terminalCount());
    }

    @Test
    void manyConcurrentRunsShareOneConnectionAndASlowListenerDelaysNoOne() throws Exception {
        // Given: 100 root sessions whose events share one stream, delayed so every run subscribes first
        int runs = 100;
        StringBuilder body = new StringBuilder(CONNECTED);
        for (int i = 0; i < runs; i++) {
            body.append(event("session.execution.started", "\"sessionID\":\"s" + i + "\""))
                    .append(text("s" + i, "answer " + i))
                    .append(event("session.execution.succeeded", "\"sessionID\":\"s" + i + "\""));
        }
        var streamRequests = new java.util.concurrent.atomic.AtomicInteger();
        server.setDispatcher(new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) {
                String path = request.getPath();
                if (path != null && path.startsWith("/api/event")) {
                    streamRequests.incrementAndGet();
                    return new MockResponse().setHeader("Content-Type", "text/event-stream")
                            .setBody(body.toString())
                            .setHeader("Content-Length", body.length() + 1024)
                            .setBodyDelay(300, TimeUnit.MILLISECONDS);
                }
                if (path != null && path.contains("/prompt")) {
                    return json("{\"data\":{\"id\":\"msg_1\"}}");
                }
                return new MockResponse().setResponseCode(404);
            }
        });
        List<OpenCodeAgentRun> started = new java.util.ArrayList<>();
        for (int i = 0; i < runs; i++) {
            AgentEventListener listener = i == 0 ? event -> sleepQuietly(2_000) : null;
            var run = new OpenCodeAgentRun("run" + i, "s" + i, AgentSpec.builder("boss").build(),
                    RunPrompt.of("hello"), listener, client, Path.of(".").toAbsolutePath(), null);
            run.start();
            started.add(run);
        }

        // When
        long begin = System.nanoTime();
        for (int i = 1; i < runs; i++) {
            var result = started.get(i).result().get(5, TimeUnit.SECONDS);
            assertTrue(result.isSuccess(), result.errorMessage());
            assertEquals("answer " + i, result.text());
        }
        long fastRunsMillis = Duration.ofNanos(System.nanoTime() - begin).toMillis();

        // Then
        assertEquals(1, streamRequests.get());
        assertTrue(fastRunsMillis < 2_000, "runs waited for the slow listener: " + fastRunsMillis + " ms");
        started.getFirst().cancel();
    }

    private static void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Test
    void ignoresTheEndOfAnExecutionItDidNotSeeStart() throws Exception {
        // Given: a stale end from a previous turn arrives before this run's own execution
        configure("", true, false);
        streamOverride = CONNECTED
                + event("session.execution.interrupted", "\"sessionID\":\"root\",\"reason\":\"user\"")
                + ROOT_STARTED + text("root", "fresh")
                + event("session.execution.succeeded", "\"sessionID\":\"root\"");
        var run = run(AgentSpec.builder("boss").build(), events::add);

        // When
        run.start();
        var result = run.result().get(3, TimeUnit.SECONDS);

        // Then
        assertTrue(result.isSuccess(), result.errorMessage());
        assertEquals("fresh", result.text());
    }

    @Test
    void usesTheFinalAssistantStepFromHistoryAsTheAnswer() throws Exception {
        // Given: the stream carries intermediate narration; history holds the steps of the turn
        configure(text("root", "Let me check the logs. ") + text("root", "Theo had 3,412 timeouts.")
                + event("session.execution.succeeded", "\"sessionID\":\"root\""), false, false);
        history = "{\"data\":["
                + "{\"id\":\"msg_4\",\"type\":\"idle\",\"time\":{\"created\":4},\"outcome\":\"succeeded\"},"
                + "{\"id\":\"msg_3\",\"type\":\"assistant\",\"time\":{\"created\":3},\"agent\":\"boss\","
                + "\"model\":{\"id\":\"m\",\"providerID\":\"p\"},"
                + "\"content\":[{\"type\":\"text\",\"text\":\"Theo had 3,412 timeouts.\"}]},"
                + "{\"id\":\"msg_2\",\"type\":\"assistant\",\"time\":{\"created\":2},\"agent\":\"boss\","
                + "\"model\":{\"id\":\"m\",\"providerID\":\"p\"},"
                + "\"content\":[{\"type\":\"text\",\"text\":\"Let me check the logs. \"}]}],\"cursor\":{}}";
        var run = run(AgentSpec.builder("boss").build(), events::add);

        // When
        run.start();
        var result = run.result().get(3, TimeUnit.SECONDS);

        // Then
        assertEquals("Theo had 3,412 timeouts.", result.text());
        assertTrue(events.stream().anyMatch(e -> e instanceof RunCompletedEvent c
                && c.finalText().equals("Theo had 3,412 timeouts.")));
    }

    @Test
    void classifiesProviderFailures() throws Exception {
        // Given
        configure(event("session.execution.failed", "\"sessionID\":\"root\",\"error\":{"
                + "\"type\":\"provider.rate-limit\",\"message\":\"slow down\",\"status\":429,"
                + "\"retryAfterMs\":2000}"), false, false);
        var run = run(AgentSpec.builder("boss").build(), events::add);

        // When
        run.start();
        var result = run.result().get(3, TimeUnit.SECONDS);

        // Then
        assertEquals(RunStatus.FAILED, result.status());
        assertEquals(FailureCategory.LLM_RATE_LIMITED, result.failureCategory());
        assertEquals(2_000L, result.retryAfterMs());
        assertEquals("slow down", result.errorMessage());
        assertTrue(events.stream().anyMatch(RunFailedEvent.class::isInstance));
    }

    @Test
    void limitsEndPartialAndCancellationEndsCancelled() throws Exception {
        // Given
        configure(step("root", 7, 0.07), true, false);
        var budget = run(AgentSpec.builder("boss").limits(RunLimits.builder().maxTokens(7).build()).build(), null);
        var timeout = run(AgentSpec.builder("boss").limits(RunLimits.builder()
                .timeout(Duration.ofMillis(300)).build()).build(), null);

        // When
        budget.start();
        timeout.start();
        var budgetResult = budget.result().get(3, TimeUnit.SECONDS);
        var timeoutResult = timeout.result().get(3, TimeUnit.SECONDS);
        var cancelled = run(AgentSpec.builder("boss").build(), null);
        cancelled.start();
        cancelled.cancel();
        var cancelledResult = cancelled.result().get(3, TimeUnit.SECONDS);

        // Then
        assertEquals(RunStatus.PARTIAL, budgetResult.status());
        assertEquals(FailureCategory.BUDGET, budgetResult.failureCategory());
        assertEquals(7, budgetResult.tokenUsage().total(), "partial results keep their accounting");
        assertEquals(RunStatus.PARTIAL, timeoutResult.status());
        assertEquals(FailureCategory.TIMEOUT, timeoutResult.failureCategory());
        assertEquals(RunStatus.CANCELLED, cancelledResult.status());
        assertFalse(cancelledResult.isSuccess());
    }

    @Test
    void waitsForTheServerToFinishAPreviousTurnBeforePrompting() throws Exception {
        // Given: the server reports the session busy twice, then idle
        configure(event("session.execution.succeeded", "\"sessionID\":\"root\""), true, false);
        var checks = new java.util.concurrent.atomic.AtomicInteger();
        activeSupplier = () -> checks.incrementAndGet() <= 2
                ? "{\"data\":{\"root\":{\"type\":\"running\"}}}" : "{\"data\":{}}";
        var run = new OpenCodeAgentRun("run", "root", AgentSpec.builder("boss").build(), RunPrompt.of("hello"),
                events::add, client, Path.of(".").toAbsolutePath(), null, true);

        // When
        run.start();
        var result = run.result().get(5, TimeUnit.SECONDS);

        // Then
        assertTrue(result.isSuccess(), result.errorMessage());
        assertEquals(3, checks.get());
    }

    @Test
    void sendsThePromptOnlyAfterTheStreamIsLive() throws Exception {
        // Given
        configure(event("session.execution.succeeded", "\"sessionID\":\"root\""), true, false);
        var run = run(AgentSpec.builder("boss").build(), events::add);

        // When
        run.start();
        run.result().get(3, TimeUnit.SECONDS);

        // Then
        assertTrue(server.takeRequest(1, TimeUnit.SECONDS).getPath().startsWith("/api/event"));
        assertTrue(server.takeRequest(1, TimeUnit.SECONDS).getPath().endsWith("/prompt"));
    }

    @Test
    void cancellationClosesBlockedStreamEvenWhenServerInterruptFails() throws Exception {
        configure(text("root", "partial"), true, true);
        var seen = new CountDownLatch(1);
        var loop = new AtomicReference<Thread>();
        var run = run(AgentSpec.builder("boss").build(), event -> {
            events.add(event);
            if (event instanceof TextDeltaEvent) {
                loop.set(Thread.currentThread());
                seen.countDown();
            }
        });
        run.start();
        assertTrue(seen.await(3, TimeUnit.SECONDS));
        run.cancel();
        run.cancel();
        var result = run.result().get(3, TimeUnit.SECONDS);
        loop.get().join(Duration.ofSeconds(3));

        assertFalse(loop.get().isAlive(), "Cancellation must stop the run's event dispatcher");
        assertFalse(result.isSuccess());
        assertEquals("partial", result.text());
        assertEquals(1, terminalCount());
        assertTrue(events.stream().anyMatch(RunInterruptedEvent.class::isInstance));
    }

    @Test
    void watchdogClosesBlockedStream() throws Exception {
        configure(text("root", "partial"), true, false);
        var loop = new AtomicReference<Thread>();
        var run = run(AgentSpec.builder("boss").limits(RunLimits.builder()
                .timeout(Duration.ofMillis(500)).build()).build(), event -> {
            events.add(event);
            if (event instanceof TextDeltaEvent) {
                loop.set(Thread.currentThread());
            }
        });
        run.start();
        var result = run.result().get(3, TimeUnit.SECONDS);
        assertFalse(result.isSuccess());
        assertTrue(events.stream().anyMatch(RunInterruptedEvent.class::isInstance));
        assertEquals(1, terminalCount());
        assertTrue(loop.get() != null);
        loop.get().join(Duration.ofSeconds(3));
        assertFalse(loop.get().isAlive());
    }

    @Test
    void listenerCancellationProducesOneConsistentTerminalSnapshot() throws Exception {
        configure(step("root", 7, 0.07) + text("root", "too late")
                + event("session.execution.succeeded", "\"sessionID\":\"root\""), false, false);
        var reference = new AtomicReference<OpenCodeAgentRun>();
        var run = run(AgentSpec.builder("boss").build(), event -> {
            events.add(event);
            if (event instanceof StepEndedEvent || event instanceof RunInterruptedEvent) {
                reference.get().cancel();
            }
        });
        reference.set(run);
        run.start();
        var result = run.result().get(3, TimeUnit.SECONDS);
        assertFalse(result.isSuccess());
        assertEquals("", result.text());
        assertEquals(7, result.tokenUsage().total());
        assertEquals(0.07, result.totalCost());
        assertEquals(1, result.steps().size());
        assertEquals(1, terminalCount());
        assertFalse(events.stream().anyMatch(TextDeltaEvent.class::isInstance));
    }

    @Test
    void budgetCancellationKeepsStepAccountingAndReleasesStream() throws Exception {
        configure(step("root", 7, 0.07), true, false);
        var run = run(AgentSpec.builder("boss").limits(RunLimits.builder().maxTokens(7).build()).build(), events::add);
        run.start();
        var result = run.result().get(3, TimeUnit.SECONDS);
        assertFalse(result.isSuccess());
        assertEquals(7, result.tokenUsage().total());
        assertEquals(0.07, result.totalCost());
        assertEquals(1, terminalCount());
    }

    @Test
    void terminalListenerMayCancelAndThrowWithoutDuplicateCompletion() throws Exception {
        configure(event("session.execution.succeeded", "\"sessionID\":\"root\""), false, false);
        var reference = new AtomicReference<OpenCodeAgentRun>();
        var run = run(AgentSpec.builder("boss").build(), event -> {
            events.add(event);
            if (event instanceof RunCompletedEvent) {
                reference.get().cancel();
                throw new IllegalStateException("listener failure");
            }
        });
        reference.set(run);
        run.start();
        assertTrue(run.result().get(3, TimeUnit.SECONDS).isSuccess());
        assertEquals(1, terminalCount());
    }

    @Test
    void concurrentCancellationAndSuccessHaveOneTerminalAccountingSnapshot() throws Exception {
        configure(step("root", 7, 0.07)
                + event("session.execution.succeeded", "\"sessionID\":\"root\""), false, false);
        var stepSeen = new CountDownLatch(1);
        var releaseStep = new CountDownLatch(1);
        var loop = new AtomicReference<Thread>();
        var run = run(AgentSpec.builder("boss").build(), event -> {
            events.add(event);
            if (event instanceof StepEndedEvent) {
                loop.set(Thread.currentThread());
                stepSeen.countDown();
                try {
                    assertTrue(releaseStep.await(3, TimeUnit.SECONDS));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(e);
                }
            }
        });
        run.start();
        assertTrue(stepSeen.await(3, TimeUnit.SECONDS));
        var cancellers = IntStream.range(0, 8).mapToObj(i -> Thread.ofVirtual().start(run::cancel)).toList();
        releaseStep.countDown();
        var result = run.result().get(3, TimeUnit.SECONDS);
        for (var canceller : cancellers) {
            canceller.join(Duration.ofSeconds(3));
            assertFalse(canceller.isAlive());
        }
        loop.get().join(Duration.ofSeconds(3));

        assertFalse(loop.get().isAlive());
        assertEquals(7, result.tokenUsage().total());
        assertEquals(0.07, result.totalCost());
        assertEquals(1, result.steps().size());
        assertEquals(1, terminalCount());
    }

    private OpenCodeAgentRun run(AgentSpec spec, AgentEventListener listener) {
        return new OpenCodeAgentRun("run", "root", spec, RunPrompt.of("hello"),
                listener, client, Path.of(".").toAbsolutePath(), null);
    }

    private long terminalCount() {
        return events.stream().filter(e -> e instanceof RunCompletedEvent
                || e instanceof RunFailedEvent || e instanceof RunInterruptedEvent).count();
    }

    /** Second and later event stream bodies; {@code null} makes reconnects fail with HTTP 503. */
    private volatile String secondStream = "";
    private volatile String active = "{\"data\":{}}";
    private volatile java.util.function.Supplier<String> activeSupplier;
    /** Replaces the whole first stream body, including server.connected, when set. */
    private volatile String streamOverride;
    private volatile String history = "{\"data\":[],\"cursor\":{}}";

    private void configure(String stream, boolean stalled, boolean failInterrupt) {
        var streamRequests = new java.util.concurrent.atomic.AtomicInteger();
        server.setDispatcher(new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) {
                String path = request.getPath();
                if (path != null && path.startsWith("/api/event")) {
                    boolean first = streamRequests.getAndIncrement() == 0;
                    if (!first && secondStream == null) {
                        return new MockResponse().setResponseCode(503);
                    }
                    // Every connection starts with server.connected; reconnections stay open.
                    String body = first && streamOverride != null ? streamOverride
                            : CONNECTED + (first ? ROOT_STARTED + stream : secondStream);
                    var response = new MockResponse().setHeader("Content-Type", "text/event-stream").setBody(body);
                    if (stalled || !first) {
                        // Keep the connection open with an unfinished body so reads block after the events.
                        response.setHeader("Content-Length", body.length() + 1024);
                    }
                    return response;
                }
                if (path != null && path.contains("/prompt")) {
                    return new MockResponse().setHeader("Content-Type", "application/json")
                            .setBody("{\"data\":{\"id\":\"msg_1\"}}");
                }
                if (path != null && path.contains("/interrupt")) {
                    return new MockResponse().setResponseCode(failInterrupt ? 500 : 204);
                }
                if (path != null && path.equals("/api/session/active")) {
                    var supplier = activeSupplier;
                    return json(supplier != null ? supplier.get() : active);
                }
                if (path != null && path.startsWith("/api/session/root/message")) {
                    return secondStream == null ? new MockResponse().setResponseCode(503) : json(history);
                }
                return new MockResponse().setResponseCode(404);
            }
        });
    }

    private static MockResponse json(String body) {
        return new MockResponse().setHeader("Content-Type", "application/json").setBody(body);
    }

    private static final String CONNECTED = "data: {\"type\":\"server.connected\",\"data\":{}}\n\n";
    private static final String ROOT_STARTED =
            "data: {\"type\":\"session.execution.started\",\"data\":{\"sessionID\":\"root\"}}\n\n";

    private static String event(String type, String data) {
        return "data: {\"type\":\"" + type + "\",\"data\":{" + data + "}}\n\n";
    }

    private static String text(String sid, String text) {
        return event("session.text.delta", "\"sessionID\":\"" + sid + "\",\"delta\":\"" + text + "\"");
    }

    private static String step(String sid, int input, double cost) {
        return event("session.step.ended", "\"sessionID\":\"" + sid + "\",\"finish\":\"stop\",\"cost\":"
                + cost + ",\"tokens\":{\"input\":" + input + "}");
    }

    private static String toolStarted(String sid, String id, String name) {
        return event("session.tool.input.started", "\"sessionID\":\"" + sid + "\",\"id\":\"" + id
                + "\",\"name\":\"" + name + "\"");
    }

    private static String toolCalled(String sid, String id, String input) {
        return event("session.tool.called", "\"sessionID\":\"" + sid + "\",\"id\":\"" + id
                + "\",\"input\":{" + input + "}");
    }
}
