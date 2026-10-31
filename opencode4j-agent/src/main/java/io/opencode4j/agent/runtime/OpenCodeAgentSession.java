package io.opencode4j.agent.runtime;

import io.opencode4j.agent.runtime.run.OpenCodeAgentRun;
import io.opencode4j.client.OpenCodeClient;
import io.opencode4j.runtime.AgentEventListener;
import io.opencode4j.runtime.AgentResult;
import io.opencode4j.runtime.AgentRun;
import io.opencode4j.runtime.AgentSession;
import io.opencode4j.runtime.FailureCategory;
import io.opencode4j.runtime.RunPrompt;
import io.opencode4j.runtime.RunStatus;
import io.opencode4j.runtime.SessionBusyException;
import io.opencode4j.runtime.SessionOptions;
import io.opencode4j.runtime.spec.AgentSpec;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;

/**
 * A conversational session backed by an OpenCode session in a shared {@link OpenCodeWorkspace}.
 *
 * <p>A session runs one prompt at a time: {@link #send} throws {@link SessionBusyException} while a run is in
 * progress. After a run that ended locally while the server may still be working (cancelled, stopped at a limit,
 * or cut off from the event stream), the next run first waits for the server to finish that turn.</p>
 */
@Slf4j
public final class OpenCodeAgentSession implements AgentSession {

    private final String sessionId;
    private final AgentSpec spec;
    private final OpenCodeClient client;
    private final OpenCodeWorkspace workspace;
    private final SessionOptions options;
    private final RunRegistry runs;
    private final Consumer<OpenCodeAgentSession> onClosed;
    private final boolean retireWorkspaceOnClose;

    private final Object lock = new Object();
    private OpenCodeAgentRun currentRun;
    private boolean serverMayBeBusy;
    private boolean closed;

    OpenCodeAgentSession(String sessionId, AgentSpec spec, OpenCodeClient client, OpenCodeWorkspace workspace,
                         SessionOptions options, RunRegistry runs, Consumer<OpenCodeAgentSession> onClosed,
                         boolean retireWorkspaceOnClose) {
        this.sessionId = Objects.requireNonNull(sessionId, "sessionId");
        this.spec = Objects.requireNonNull(spec, "spec");
        this.client = Objects.requireNonNull(client, "client");
        this.workspace = Objects.requireNonNull(workspace, "workspace");
        this.options = options == null ? SessionOptions.defaults() : options;
        this.runs = Objects.requireNonNull(runs, "runs");
        this.onClosed = Objects.requireNonNull(onClosed, "onClosed");
        this.retireWorkspaceOnClose = retireWorkspaceOnClose;
    }

    @Override
    public String sessionId() {
        return sessionId;
    }

    @Override
    public AgentSpec spec() {
        return spec;
    }

    public Path workspaceDir() {
        return workspace.directory();
    }

    @Override
    public Optional<AgentRun> activeRun() {
        synchronized (lock) {
            return currentRun == null || currentRun.result().isDone() ? Optional.empty() : Optional.of(currentRun);
        }
    }

    @Override
    public AgentRun send(RunPrompt prompt, AgentEventListener listener) {
        Objects.requireNonNull(prompt, "prompt");
        OpenCodeAgentRun run;
        synchronized (lock) {
            if (closed) {
                throw new IllegalStateException("Session " + sessionId + " is closed");
            }
            if (currentRun != null && !currentRun.result().isDone()) {
                throw new SessionBusyException(sessionId,
                        "Session " + sessionId + " is still running " + currentRun.runId());
            }
            String runId = "run_" + UUID.randomUUID().toString().substring(0, 12);
            run = new OpenCodeAgentRun(runId, sessionId, spec, prompt, listener, client, workspace.directory(),
                    workspace.toolServer(), serverMayBeBusy);
            currentRun = run;
        }
        if (workspace.toolServer() != null) {
            // Discard a result a previous, unfinished run may have left behind.
            workspace.toolServer().takeSubmittedResult(sessionId);
        }
        runs.register(run);
        run.result().whenComplete((result, failure) -> afterRun(run, result));
        run.start();
        return run;
    }

    private void afterRun(OpenCodeAgentRun run, AgentResult result) {
        runs.unregister(run);
        synchronized (lock) {
            serverMayBeBusy = result == null || mayLeaveServerRunning(result);
        }
    }

    private static boolean mayLeaveServerRunning(AgentResult result) {
        return result.status() == RunStatus.CANCELLED || result.status() == RunStatus.PARTIAL
                || result.failureCategory() == FailureCategory.TRANSPORT;
    }

    @Override
    public void detach() {
        if (!beginClose()) {
            return;
        }
        OpenCodeAgentRun active = takeActiveRun();
        if (active != null) {
            active.detach();
        }
        release();
    }

    @Override
    public void delete() {
        if (!beginClose()) {
            return;
        }
        OpenCodeAgentRun active = takeActiveRun();
        if (active != null) {
            active.cancel();
        }
        try {
            // Deleting the root session cascades to its sub-agent sessions.
            client.sessions().delete(sessionId);
            log.debug("Deleted OpenCode session {}", sessionId);
        } catch (RuntimeException e) {
            log.debug("Session {} cleanup: {}", sessionId, e.getMessage());
        }
        release();
    }

    @Override
    public void close() {
        if (options.deleteOnClose()) {
            delete();
        } else {
            detach();
        }
    }

    /** Marks the session closed; {@code false} if it already was. */
    private boolean beginClose() {
        synchronized (lock) {
            if (closed) {
                return false;
            }
            closed = true;
            return true;
        }
    }

    private OpenCodeAgentRun takeActiveRun() {
        synchronized (lock) {
            OpenCodeAgentRun active = currentRun;
            currentRun = null;
            return active != null && !active.result().isDone() ? active : null;
        }
    }

    private void release() {
        workspace.release(this);
        onClosed.accept(this);
        if (retireWorkspaceOnClose && workspace.openSessionCount() == 0) {
            workspace.retire();
        }
    }
}
