package io.opencode4j.agent.runtime.run;

import io.opencode4j.agent.runtime.mcp.JvmMcpServer;
import io.opencode4j.client.OpenCodeClient;
import io.opencode4j.client.SseStream;
import io.opencode4j.event.ExecutionOutcome;
import io.opencode4j.event.SseEventParser;
import io.opencode4j.runtime.AgentEventListener;
import io.opencode4j.runtime.AgentResult;
import io.opencode4j.runtime.AgentRun;
import io.opencode4j.runtime.RunPrompt;
import io.opencode4j.runtime.event.RunCompletedEvent;
import io.opencode4j.runtime.event.RunFailedEvent;
import io.opencode4j.runtime.event.RunInterruptedEvent;
import io.opencode4j.runtime.event.RunStartedEvent;
import io.opencode4j.runtime.spec.AgentSpec;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

/**
 * Executes a single prompt turn, coordinating the stream and run lifecycle.
 */
@Slf4j
public final class OpenCodeAgentRun implements AgentRun {
    private final String runId;
    private final String rootSessionId;
    private final AgentSpec spec;
    private final RunPrompt prompt;
    private final OpenCodeClient client;
    private final Path workspaceDir;
    private final JvmMcpServer jvmMcpServer;
    private final CompletableFuture<AgentResult> future = new CompletableFuture<>();

    // Event processing and terminal snapshots share one lock, including reentrant listener cancellation.
    private final Object stateLock = new Object();
    private final RunEventProcessor eventProcessor;
    private boolean terminal;
    private boolean started;
    private SseStream activeStream;

    public OpenCodeAgentRun(String runId, String rootSessionId, AgentSpec spec, RunPrompt prompt,
                            AgentEventListener listener, OpenCodeClient client, Path workspaceDir,
                            JvmMcpServer jvmMcpServer) {
        this.runId = Objects.requireNonNull(runId, "runId");
        this.rootSessionId = Objects.requireNonNull(rootSessionId, "rootSessionId");
        this.spec = Objects.requireNonNull(spec, "spec");
        this.prompt = Objects.requireNonNull(prompt, "prompt");
        this.client = Objects.requireNonNull(client, "client");
        this.workspaceDir = Objects.requireNonNull(workspaceDir, "workspaceDir");
        this.jvmMcpServer = jvmMcpServer;
        eventProcessor = new RunEventProcessor(runId, rootSessionId, spec, client, listener,
                this::isTerminal, this::finish, this::cancel);
    }

    public void start() {
        synchronized (stateLock) {
            if (started || terminal) {
                return;
            }
            started = true;
            Thread.ofVirtual().name("opencode-run-" + runId).start(this::runLoop);
        }
    }

    private void runLoop() {
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
        val watchdog = startWatchdog();
        try (val stream = client.events().open(workspaceDir.toString())) {
            synchronized (stateLock) {
                if (terminal) {
                    return;
                }
                activeStream = stream;
            }
            sendPromptTurn();
            while (!future.isDone()) {
                val line = stream.readLine();
                if (line == null) {
                    finish(ExecutionOutcome.FAILED, "Event stream ended before root execution completed");
                    break;
                }
                val event = SseEventParser.parseLine(line);
                if (event != null) {
                    processEvent(event);
                }
            }
        } catch (IOException | RuntimeException e) {
            synchronized (stateLock) {
                if (!terminal) {
                    log.error("Run loop failed on runId {}: {}", runId, e.getMessage(), e);
                    finish(ExecutionOutcome.FAILED, "Event stream failure: " + e.getMessage());
                }
            }
        } finally {
            synchronized (stateLock) {
                activeStream = null;
            }
            if (watchdog != null) {
                watchdog.interrupt();
            }
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
        log.debug("Sent prompt to session {}: receiptId={}", rootSessionId, receipt.id());
    }

    private void processEvent(io.opencode4j.event.OpenCodeEvent event) {
        synchronized (stateLock) {
            eventProcessor.process(event);
        }
    }

    private void finish(ExecutionOutcome outcome, String detail) {
        synchronized (stateLock) {
            if (terminal || future.isDone()) {
                return;
            }
            terminal = true;
            boolean success = outcome == ExecutionOutcome.SUCCEEDED;
            String reason = detail != null ? detail
                    : outcome == ExecutionOutcome.INTERRUPTED ? "Cancelled or interrupted" : "Execution failed";
            val text = eventProcessor.text();
            val result = eventProcessor.result(success, reason, spec.steps(),
                    success && jvmMcpServer != null ? jvmMcpServer.submittedResult() : null);
            long now = System.currentTimeMillis();
            eventProcessor.emit(switch (outcome) {
                case SUCCEEDED -> new RunCompletedEvent(runId, rootSessionId, spec.name(), spec.name(), now, text);
                case FAILED -> new RunFailedEvent(runId, rootSessionId, spec.name(), spec.name(), now, reason);
                case INTERRUPTED -> new RunInterruptedEvent(runId, rootSessionId, spec.name(), spec.name(), now, reason);
            });
            future.complete(result);
        }
    }

    private boolean isTerminal() {
        return terminal;
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
        SseStream stream;
        synchronized (stateLock) {
            if (terminal || future.isDone()) {
                return;
            }
            stream = activeStream;
            finish(ExecutionOutcome.INTERRUPTED, "Cancelled or interrupted");
        }
        try {
            if (stream != null) {
                stream.close();
            }
        } finally {
            try {
                client.sessions().interrupt(rootSessionId);
                log.debug("Interrupted root session {} for run {}", rootSessionId, runId);
            } catch (RuntimeException e) {
                log.warn("Failed to interrupt root session {}: {}", rootSessionId, e.getMessage());
            }
        }
    }
}
