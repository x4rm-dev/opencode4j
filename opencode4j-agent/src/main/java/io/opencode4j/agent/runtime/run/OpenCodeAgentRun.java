package io.opencode4j.agent.runtime.run;

import io.opencode4j.OpenCodeException;
import io.opencode4j.agent.runtime.mcp.JvmMcpServer;
import io.opencode4j.client.OpenCodeClient;
import io.opencode4j.client.event.EventBus;
import io.opencode4j.client.event.EventFilter;
import io.opencode4j.client.event.EventSink;
import io.opencode4j.client.event.StreamGap;
import io.opencode4j.client.event.Subscription;
import io.opencode4j.client.event.SubscriptionOptions;
import io.opencode4j.event.EventEnvelope;
import io.opencode4j.event.OpenCodeEvent;
import io.opencode4j.runtime.AgentEventListener;
import io.opencode4j.runtime.AgentResult;
import io.opencode4j.runtime.AgentRun;
import io.opencode4j.runtime.FailureCategory;
import io.opencode4j.runtime.RunPrompt;
import io.opencode4j.runtime.RunStatus;
import io.opencode4j.runtime.event.RunCompletedEvent;
import io.opencode4j.runtime.event.RunFailedEvent;
import io.opencode4j.runtime.event.RunInterruptedEvent;
import io.opencode4j.runtime.event.RunStartedEvent;
import io.opencode4j.runtime.spec.AgentSpec;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

/**
 * Executes a single prompt turn on the client's shared {@link EventBus}.
 *
 * <p>The run subscribes to its session tree, waits until the stream is live, and only then sends the prompt,
 * so no early event is lost. Events are processed on the subscription's own dispatcher thread, never on the
 * shared reader. When the bus reports a {@link StreamGap} (a reconnect or a queue overflow), the run
 * reconciles from the session history: if the session is idle the run ends with the recorded outcome and the
 * final answer from history; otherwise it keeps listening.</p>
 *
 * <p>A run ends {@link RunStatus#PARTIAL} when a budget or the wall-clock timeout stops it, and
 * {@link RunStatus#CANCELLED} when the caller cancels it; both interrupt the server session. The final answer
 * of a successful run is the last assistant step's text from history, so the narration of intermediate steps is
 * not part of it.</p>
 */
@Slf4j
public final class OpenCodeAgentRun implements AgentRun {

    private static final Duration LIVE_TIMEOUT = Duration.ofSeconds(15);
    private static final Duration SERVER_IDLE_TIMEOUT = Duration.ofSeconds(10);
    private static final long SERVER_IDLE_POLL_MILLIS = 200;

    private final String runId;
    private final String rootSessionId;
    private final AgentSpec spec;
    private final RunPrompt prompt;
    private final OpenCodeClient client;
    private final JvmMcpServer jvmMcpServer;
    private final boolean awaitServerIdle;
    private final RunOptions options;
    private final long startedNanos = System.nanoTime();
    private final CompletableFuture<AgentResult> future = new CompletableFuture<>();
    private final RunReconciler reconciler;

    // Event processing and terminal snapshots share one lock, including reentrant listener cancellation.
    private final Object stateLock = new Object();
    private final RunEventProcessor eventProcessor;
    // Events that arrive before the prompt is accepted are held and processed right after it, in order.
    private final List<OpenCodeEvent> heldEvents = new ArrayList<>();
    private boolean promptAccepted;
    private boolean terminal;
    private boolean started;
    private Subscription subscription;
    private RunEnd pendingEnd;
    private volatile String promptMessageId;

    public OpenCodeAgentRun(String runId, String rootSessionId, AgentSpec spec, RunPrompt prompt,
                            AgentEventListener listener, OpenCodeClient client, Path workspaceDir,
                            JvmMcpServer jvmMcpServer) {
        this(runId, rootSessionId, spec, prompt, listener, client, workspaceDir, jvmMcpServer, false);
    }

    /**
     * @param awaitServerIdle wait (up to 10 s) for the server to finish an execution still running in the
     *                        session, for example after a previous run was cancelled locally, before prompting
     */
    public OpenCodeAgentRun(String runId, String rootSessionId, AgentSpec spec, RunPrompt prompt,
                            AgentEventListener listener, OpenCodeClient client, Path workspaceDir,
                            JvmMcpServer jvmMcpServer, boolean awaitServerIdle) {
        this(runId, rootSessionId, spec, prompt, listener, client, workspaceDir, jvmMcpServer,
                RunOptions.defaults().awaitingServerIdle(awaitServerIdle));
    }

    /** @param options server-idle wait, metrics and redaction for this run */
    public OpenCodeAgentRun(String runId, String rootSessionId, AgentSpec spec, RunPrompt prompt,
                            AgentEventListener listener, OpenCodeClient client, Path workspaceDir,
                            JvmMcpServer jvmMcpServer, RunOptions options) {
        this.options = Objects.requireNonNull(options, "options");
        boolean awaitServerIdle = options.awaitServerIdle();
        this.runId = Objects.requireNonNull(runId, "runId");
        this.rootSessionId = Objects.requireNonNull(rootSessionId, "rootSessionId");
        this.spec = Objects.requireNonNull(spec, "spec");
        this.prompt = Objects.requireNonNull(prompt, "prompt");
        this.client = Objects.requireNonNull(client, "client");
        Objects.requireNonNull(workspaceDir, "workspaceDir");
        this.jvmMcpServer = jvmMcpServer;
        this.awaitServerIdle = awaitServerIdle;
        this.reconciler = new RunReconciler(client, rootSessionId);
        eventProcessor = new RunEventProcessor(runId, rootSessionId, spec, client, listener,
                this::isTerminal, this::onRootEnded, this::stop, options.redactor());
    }

    public void start() {
        synchronized (stateLock) {
            if (started || terminal) {
                return;
            }
            started = true;
            Thread.ofVirtual().name("opencode-run-" + runId).start(this::begin);
        }
    }

    private void begin() {
        try (var ignored = RunMdc.of(runId, rootSessionId)) {
            beginWithContext();
        }
    }

    private void beginWithContext() {
        synchronized (stateLock) {
            if (terminal) {
                return;
            }
            eventProcessor.emit(new RunStartedEvent(runId, rootSessionId, spec.name(), spec.name(),
                    System.currentTimeMillis(), prompt.text()));
            if (terminal) {
                return;
            }
        }
        scheduleTimeout();
        try {
            if (awaitServerIdle && !waitForServerIdle()) {
                finish(RunEnd.failed(FailureCategory.SESSION_BUSY, "Session " + rootSessionId
                        + " is still running a previous turn on the server"));
                return;
            }
            EventBus bus = client.eventBus();
            Subscription opened = bus.subscribe(EventFilter.sessionTree(rootSessionId), new RunSink(),
                    SubscriptionOptions.defaults().named(runId));
            synchronized (stateLock) {
                if (terminal) {
                    opened.close();
                    return;
                }
                subscription = opened;
            }
            bus.awaitLive().get(LIVE_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
            synchronized (stateLock) {
                if (terminal) {
                    return;
                }
            }
            sendPromptTurn();
        } catch (TimeoutException e) {
            finish(RunEnd.failed(FailureCategory.TRANSPORT,
                    "Event stream did not become live within " + LIVE_TIMEOUT.toSeconds() + " s"));
        } catch (ExecutionException e) {
            finish(RunEnd.failed(FailureCategory.TRANSPORT, "Event stream failed: " + e.getCause().getMessage()));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            finish(RunEnd.cancelled("Interrupted while starting the run"));
        } catch (OpenCodeException e) {
            log.error("Run {} failed to send its prompt: {}", runId, e.getMessage());
            finish(RunEnd.failed(FailureCategory.TRANSPORT, "Prompt failed: " + e.getMessage()));
        } catch (RuntimeException | Error e) {
            // Errors (for example a LinkageError from a dependency clash) must also end the run: if the run
            // never completed its future, await() would block forever.
            log.error("Run {} failed to start: {}", runId, e.getMessage(), e);
            finish(RunEnd.failed(FailureCategory.EXECUTION, "Run start failed: " + e));
        }
    }

    private void scheduleTimeout() {
        val timeout = spec.limits().timeout();
        if (timeout == null || timeout.isZero() || timeout.isNegative()) {
            return;
        }
        // The JDK's shared delayer thread fires the timeout; no thread is held per run.
        CompletableFuture.delayedExecutor(timeout.toMillis(), TimeUnit.MILLISECONDS).execute(() -> {
            if (!future.isDone()) {
                log.warn("Run {} exceeded wall-clock timeout of {}; interrupting", runId, timeout);
                stop(RunEnd.partial(FailureCategory.TIMEOUT, "Wall-clock timeout of " + timeout + " elapsed"));
            }
        });
    }

    private boolean waitForServerIdle() throws InterruptedException {
        long deadline = System.nanoTime() + SERVER_IDLE_TIMEOUT.toNanos();
        while (true) {
            try {
                if (!client.sessions().activeSessionIds().contains(rootSessionId)) {
                    return true;
                }
            } catch (OpenCodeException e) {
                log.debug("Could not read active sessions; assuming {} is idle: {}", rootSessionId, e.getMessage());
                return true;
            }
            if (System.nanoTime() > deadline || isTerminal()) {
                return false;
            }
            Thread.sleep(SERVER_IDLE_POLL_MILLIS);
        }
    }

    private void sendPromptTurn() {
        val receipt = client.prompts().send(rootSessionId, RunPromptFactory.create(runId, prompt, spec));
        promptMessageId = receipt.id();
        log.debug("Sent prompt to session {}: receiptId={}", rootSessionId, receipt.id());
        RunEnd end;
        synchronized (stateLock) {
            promptAccepted = true;
            for (OpenCodeEvent held : heldEvents) {
                eventProcessor.process(held);
            }
            heldEvents.clear();
            end = takePendingEnd();
        }
        completeFromHistory(end);
    }

    private void processEvent(OpenCodeEvent event) {
        RunEnd end;
        synchronized (stateLock) {
            if (!promptAccepted) {
                heldEvents.add(event);
                return;
            }
            eventProcessor.process(event);
            end = takePendingEnd();
        }
        completeFromHistory(end);
    }

    /** Called by the processor, under the state lock, when the root execution ends. */
    private void onRootEnded(RunEnd end) {
        if (pendingEnd == null) {
            pendingEnd = end;
        }
    }

    private RunEnd takePendingEnd() {
        RunEnd end = pendingEnd;
        pendingEnd = null;
        return end;
    }

    /** Finishes with {@code end}, reading the final answer from history first. Must not hold the state lock. */
    private void completeFromHistory(RunEnd end) {
        if (end == null) {
            return;
        }
        String answer = end.status() == RunStatus.SUCCEEDED ? reconciler.finalText(promptMessageId) : null;
        synchronized (stateLock) {
            if (terminal) {
                return;
            }
            if (answer != null) {
                eventProcessor.useReconciledText(answer);
            }
            finish(end);
        }
    }

    private void reconcile(StreamGap gap) {
        if (isTerminal()) {
            return;
        }
        log.info("Run {} may have missed events ({}); reconciling session {} from history",
                runId, gap.reason(), rootSessionId);
        val outcome = reconciler.reconcile(promptMessageId);
        if (outcome == null) {
            return;
        }
        synchronized (stateLock) {
            if (terminal) {
                return;
            }
            if (outcome.finalText() != null) {
                eventProcessor.useReconciledText(outcome.finalText());
            }
            finish(outcome.end());
        }
    }

    private void finish(RunEnd end) {
        Subscription closing;
        synchronized (stateLock) {
            if (terminal || future.isDone()) {
                return;
            }
            terminal = true;
            val text = eventProcessor.text();
            val result = eventProcessor.result(end, spec.effectiveSteps(),
                    end.status() == RunStatus.SUCCEEDED && jvmMcpServer != null
                            ? jvmMcpServer.takeSubmittedResult(rootSessionId) : null);
            long now = System.currentTimeMillis();
            String reason = result.errorMessage();
            eventProcessor.emit(end.status() == RunStatus.SUCCEEDED
                    ? new RunCompletedEvent(runId, rootSessionId, spec.name(), spec.name(), now, text)
                    : end.interrupted()
                            ? new RunInterruptedEvent(runId, rootSessionId, spec.name(), spec.name(), now, reason)
                            : new RunFailedEvent(runId, rootSessionId, spec.name(), spec.name(), now, reason));
            closing = subscription;
            subscription = null;
            // Release the stream before completing, so a caller that sees the result sees the run's resources gone.
            if (closing != null) {
                closing.close();
            }
            report(result);
            future.complete(result);
        }
    }

    private void report(AgentResult result) {
        try {
            options.metrics().runFinished(new io.opencode4j.agent.runtime.observe.RunSummary(runId, rootSessionId,
                    spec.name(), spec.model(), result.status(), result.failureCategory(),
                    Duration.ofNanos(System.nanoTime() - startedNanos).toMillis(), eventProcessor.usage(),
                    eventProcessor.cost(), eventProcessor.stepCount(), eventProcessor.toolCallCount()));
        } catch (RuntimeException e) {
            log.debug("Run metrics failed: {}", e.toString());
        }
    }

    /** Ends the run with {@code end} and interrupts the server session, which is still working. */
    private void stop(RunEnd end) {
        synchronized (stateLock) {
            if (terminal || future.isDone()) {
                return;
            }
            finish(end);
        }
        try {
            client.sessions().interrupt(rootSessionId);
            log.debug("Interrupted root session {} for run {}", rootSessionId, runId);
        } catch (RuntimeException e) {
            log.warn("Failed to interrupt root session {}: {}", rootSessionId, e.getMessage());
        }
    }

    private boolean isTerminal() {
        synchronized (stateLock) {
            return terminal;
        }
    }

    /**
     * The agent name and path of a session in this run's tree, or {@code null} if the session is not part of
     * it. Used to describe the caller of an in-JVM tool.
     */
    public RunIdentity identityOf(String sessionId) {
        synchronized (stateLock) {
            val identity = eventProcessor.identity(sessionId);
            return identity == null ? null : new RunIdentity(runId, rootSessionId, identity.name(), identity.path());
        }
    }

    @Override
    public String runId() {
        return runId;
    }

    @Override
    public String sessionId() {
        return rootSessionId;
    }

    @Override
    public CompletableFuture<AgentResult> result() {
        return future;
    }

    @Override
    public AgentResult await() {
        return future.join();
    }

    @Override
    public void cancel() {
        stop(RunEnd.cancelled("Cancelled or interrupted"));
    }

    /**
     * Stops observing without interrupting the server: the run ends locally as cancelled while the server may
     * finish the turn; its outcome stays readable from the session history.
     */
    public void detach() {
        finish(RunEnd.cancelled("Detached; the server may still finish the turn"));
    }

    /** Receives this run's events on the subscription's dispatcher thread. */
    private final class RunSink implements EventSink {

        @Override
        public void onEvent(EventEnvelope envelope) {
            try (var ignored = RunMdc.of(runId, envelope.event().sessionId() != null
                    ? envelope.event().sessionId() : rootSessionId)) {
                processEvent(envelope.event());
            } catch (RuntimeException | Error e) {
                log.error("Event processing failed on runId {}: {}", runId, e.getMessage(), e);
                finish(RunEnd.failed(FailureCategory.EXECUTION, "Event processing failure: " + e));
            }
        }

        @Override
        public void onGap(StreamGap gap) {
            try (var ignored = RunMdc.of(runId, rootSessionId)) {
                reconcile(gap);
            } catch (RuntimeException e) {
                log.warn("Reconciling run {} failed; waiting for live events: {}", runId, e.toString());
            }
        }

        @Override
        public void onFailure(Throwable cause) {
            finish(RunEnd.failed(FailureCategory.TRANSPORT, "Event stream failed: " + cause.getMessage()));
        }
    }
}
