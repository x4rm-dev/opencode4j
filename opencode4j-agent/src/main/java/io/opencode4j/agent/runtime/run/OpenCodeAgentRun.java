package io.opencode4j.agent.runtime.run;

import io.opencode4j.agent.runtime.mcp.JvmMcpServer;
import io.opencode4j.client.OpenCodeClient;
import io.opencode4j.client.event.EventBus;
import io.opencode4j.client.event.EventFilter;
import io.opencode4j.client.event.EventSink;
import io.opencode4j.client.event.StreamGap;
import io.opencode4j.client.event.Subscription;
import io.opencode4j.client.event.SubscriptionOptions;
import io.opencode4j.event.EventEnvelope;
import io.opencode4j.event.ExecutionOutcome;
import io.opencode4j.event.OpenCodeEvent;
import io.opencode4j.runtime.AgentEventListener;
import io.opencode4j.runtime.AgentResult;
import io.opencode4j.runtime.AgentRun;
import io.opencode4j.runtime.RunPrompt;
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
import java.util.concurrent.CancellationException;
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
 */
@Slf4j
public final class OpenCodeAgentRun implements AgentRun {

    private static final Duration LIVE_TIMEOUT = Duration.ofSeconds(15);

    private final String runId;
    private final String rootSessionId;
    private final AgentSpec spec;
    private final RunPrompt prompt;
    private final OpenCodeClient client;
    private final JvmMcpServer jvmMcpServer;
    private final CompletableFuture<AgentResult> future = new CompletableFuture<>();
    private final RunReconciler reconciler;

    // Event processing and terminal snapshots share one lock, including reentrant listener cancellation.
    private final Object stateLock = new Object();
    private final RunEventProcessor eventProcessor;
    private boolean terminal;
    private boolean started;
    private Subscription subscription;
    private Thread watchdog;
    private volatile String promptMessageId;
    // Events that arrive before the prompt is accepted are held and processed right after it, in order.
    private final List<OpenCodeEvent> heldEvents = new ArrayList<>();
    private boolean promptAccepted;

    public OpenCodeAgentRun(String runId, String rootSessionId, AgentSpec spec, RunPrompt prompt,
                            AgentEventListener listener, OpenCodeClient client, Path workspaceDir,
                            JvmMcpServer jvmMcpServer) {
        this.runId = Objects.requireNonNull(runId, "runId");
        this.rootSessionId = Objects.requireNonNull(rootSessionId, "rootSessionId");
        this.spec = Objects.requireNonNull(spec, "spec");
        this.prompt = Objects.requireNonNull(prompt, "prompt");
        this.client = Objects.requireNonNull(client, "client");
        Objects.requireNonNull(workspaceDir, "workspaceDir");
        this.jvmMcpServer = jvmMcpServer;
        this.reconciler = new RunReconciler(client, rootSessionId);
        eventProcessor = new RunEventProcessor(runId, rootSessionId, spec, client, listener,
                this::isTerminal, this::finish, this::cancel);
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
        synchronized (stateLock) {
            if (terminal) {
                return;
            }
            eventProcessor.emit(new RunStartedEvent(runId, rootSessionId, spec.name(), spec.name(),
                    System.currentTimeMillis(), prompt.text()));
            if (terminal) {
                return;
            }
            watchdog = startWatchdog();
        }
        try {
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
            finish(ExecutionOutcome.FAILED, "Event stream did not become live within " + LIVE_TIMEOUT.toSeconds() + " s");
        } catch (ExecutionException e) {
            finish(ExecutionOutcome.FAILED, "Event stream failed: " + e.getCause().getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            finish(ExecutionOutcome.INTERRUPTED, "Interrupted while starting the run");
        } catch (RuntimeException | Error e) {
            // Errors (for example a LinkageError from a dependency clash) must also end the run: if the run
            // never completed its future, await() would block forever.
            log.error("Run {} failed to start: {}", runId, e.getMessage(), e);
            finish(ExecutionOutcome.FAILED, "Run start failed: " + e);
        }
    }

    private Thread startWatchdog() {
        val timeout = spec.limits().timeout();
        if (timeout == null || timeout.isZero() || timeout.isNegative()) {
            return null;
        }
        return Thread.ofVirtual().name("watchdog-" + runId).start(() -> {
            try {
                future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
            } catch (TimeoutException e) {
                log.warn("Run {} exceeded wall-clock timeout of {}; interrupting", runId, timeout);
                cancel();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (ExecutionException | CancellationException e) {
                log.debug("Run {} result no longer available to watchdog", runId, e);
            }
        });
    }

    private void sendPromptTurn() {
        val receipt = client.prompts().send(rootSessionId, RunPromptFactory.create(runId, prompt, spec));
        promptMessageId = receipt.id();
        log.debug("Sent prompt to session {}: receiptId={}", rootSessionId, receipt.id());
        synchronized (stateLock) {
            promptAccepted = true;
            for (OpenCodeEvent held : heldEvents) {
                eventProcessor.process(held);
            }
            heldEvents.clear();
        }
    }

    private void processEvent(OpenCodeEvent event) {
        synchronized (stateLock) {
            if (!promptAccepted) {
                heldEvents.add(event);
                return;
            }
            eventProcessor.process(event);
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
            finish(outcome.outcome(), outcome.detail());
        }
    }

    private void finish(ExecutionOutcome outcome, String detail) {
        Subscription closing;
        Thread stopping;
        synchronized (stateLock) {
            if (terminal || future.isDone()) {
                return;
            }
            terminal = true;
            boolean success = outcome == ExecutionOutcome.SUCCEEDED;
            String reason = detail != null ? detail
                    : outcome == ExecutionOutcome.INTERRUPTED ? "Cancelled or interrupted" : "Execution failed";
            val text = eventProcessor.text();
            val result = eventProcessor.result(success, reason, spec.effectiveSteps(),
                    success && jvmMcpServer != null ? jvmMcpServer.submittedResult() : null);
            long now = System.currentTimeMillis();
            eventProcessor.emit(switch (outcome) {
                case SUCCEEDED -> new RunCompletedEvent(runId, rootSessionId, spec.name(), spec.name(), now, text);
                case FAILED -> new RunFailedEvent(runId, rootSessionId, spec.name(), spec.name(), now, reason);
                case INTERRUPTED -> new RunInterruptedEvent(runId, rootSessionId, spec.name(), spec.name(), now, reason);
            });
            future.complete(result);
            closing = subscription;
            subscription = null;
            stopping = watchdog;
            watchdog = null;
        }
        if (closing != null) {
            closing.close();
        }
        if (stopping != null && stopping != Thread.currentThread()) {
            stopping.interrupt();
        }
    }

    private boolean isTerminal() {
        synchronized (stateLock) {
            return terminal;
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
        synchronized (stateLock) {
            if (terminal || future.isDone()) {
                return;
            }
            finish(ExecutionOutcome.INTERRUPTED, "Cancelled or interrupted");
        }
        try {
            client.sessions().interrupt(rootSessionId);
            log.debug("Interrupted root session {} for run {}", rootSessionId, runId);
        } catch (RuntimeException e) {
            log.warn("Failed to interrupt root session {}: {}", rootSessionId, e.getMessage());
        }
    }

    /** Receives this run's events on the subscription's dispatcher thread. */
    private final class RunSink implements EventSink {

        @Override
        public void onEvent(EventEnvelope envelope) {
            try {
                processEvent(envelope.event());
            } catch (RuntimeException | Error e) {
                log.error("Event processing failed on runId {}: {}", runId, e.getMessage(), e);
                finish(ExecutionOutcome.FAILED, "Event processing failure: " + e);
            }
        }

        @Override
        public void onGap(StreamGap gap) {
            try {
                reconcile(gap);
            } catch (RuntimeException e) {
                log.warn("Reconciling run {} failed; waiting for live events: {}", runId, e.toString());
            }
        }

        @Override
        public void onFailure(Throwable cause) {
            finish(ExecutionOutcome.FAILED, "Event stream failed: " + cause.getMessage());
        }
    }
}
