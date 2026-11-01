package io.opencode4j.agent.runtime.run;

import io.opencode4j.client.OpenCodeClient;
import io.opencode4j.event.ExecutionEnded;
import io.opencode4j.event.ExecutionStarted;
import io.opencode4j.event.StepStarted;
import io.opencode4j.event.OpenCodeEvent;
import io.opencode4j.event.PermissionAsked;
import io.opencode4j.event.ReasoningDelta;
import io.opencode4j.event.SessionCreated;
import io.opencode4j.event.StepEnded;
import io.opencode4j.event.StepFailed;
import io.opencode4j.event.TextDelta;
import io.opencode4j.event.ToolCalled;
import io.opencode4j.event.ToolFailed;
import io.opencode4j.event.ToolStarted;
import io.opencode4j.event.ToolSucceeded;
import io.opencode4j.model.PermissionDecision;
import io.opencode4j.runtime.AgentEventListener;
import io.opencode4j.runtime.AgentResult;
import io.opencode4j.runtime.FailureCategory;
import io.opencode4j.runtime.RunStatus;
import io.opencode4j.runtime.TextRedactor;
import io.opencode4j.runtime.event.StepStartedEvent;
import io.opencode4j.runtime.ToolExecution;
import io.opencode4j.runtime.event.AgentEvent;
import io.opencode4j.runtime.event.DelegationCompletedEvent;
import io.opencode4j.runtime.event.DelegationStartedEvent;
import io.opencode4j.runtime.event.PermissionRequestedEvent;
import io.opencode4j.runtime.event.ReasoningDeltaEvent;
import io.opencode4j.runtime.event.StepEndedEvent;
import io.opencode4j.runtime.event.TextDeltaEvent;
import io.opencode4j.runtime.event.ToolCallCompletedEvent;
import io.opencode4j.runtime.event.ToolCallFailedEvent;
import io.opencode4j.runtime.event.ToolCallStartedEvent;
import io.opencode4j.runtime.spec.AgentSpec;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.BooleanSupplier;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

/**
 * Translates server events and accounts run output. All methods that read or mutate state are called
 * while holding the run's state lock, also used for terminal snapshots.
 */
@Slf4j
final class RunEventProcessor {
    private final String runId;
    private final String rootSessionId;
    private final AgentSpec spec;
    private final OpenCodeClient client;
    private final AgentEventListener listener;
    private final BooleanSupplier terminal;
    private final Consumer<RunEnd> rootEnded;
    private final Consumer<RunEnd> limitReached;
    private final RunSessionHierarchy hierarchy;
    private final RunToolTracker tools = new RunToolTracker();
    private final RunAccounting accounting = new RunAccounting();
    private String reconciledText;
    private boolean rootStarted;
    private final TextRedactor redactor;
    private final Set<String> runningChildren = new HashSet<>();

    RunEventProcessor(String runId, String rootSessionId, AgentSpec spec, OpenCodeClient client,
                      AgentEventListener listener, BooleanSupplier terminal,
                      Consumer<RunEnd> rootEnded, Consumer<RunEnd> limitReached) {
        this(runId, rootSessionId, spec, client, listener, terminal, rootEnded, limitReached, TextRedactor.none());
    }

    RunEventProcessor(String runId, String rootSessionId, AgentSpec spec, OpenCodeClient client,
                      AgentEventListener listener, BooleanSupplier terminal,
                      Consumer<RunEnd> rootEnded, Consumer<RunEnd> limitReached, TextRedactor redactor) {
        this.redactor = redactor == null ? TextRedactor.none() : redactor;
        this.runId = Objects.requireNonNull(runId, "runId");
        this.rootSessionId = Objects.requireNonNull(rootSessionId, "rootSessionId");
        this.spec = Objects.requireNonNull(spec, "spec");
        this.client = Objects.requireNonNull(client, "client");
        this.listener = listener;
        this.terminal = Objects.requireNonNull(terminal, "terminal");
        this.rootEnded = Objects.requireNonNull(rootEnded, "rootEnded");
        this.limitReached = Objects.requireNonNull(limitReached, "limitReached");
        hierarchy = new RunSessionHierarchy(rootSessionId, spec.name());
    }

    void process(OpenCodeEvent event) {
        if (terminal.getAsBoolean()) {
            return;
        }
        if (event instanceof SessionCreated created) {
            hierarchy.register(created);
            if (hierarchy.get(created.sessionId()) != null && !rootSessionId.equals(created.sessionId())) {
                childStarted(created.sessionId());
            }
            return;
        }
        String sid = switch (event) {
            case TextDelta e -> e.sessionId();
            case ReasoningDelta e -> e.sessionId();
            case ToolStarted e -> e.sessionId();
            case ToolCalled e -> e.sessionId();
            case ToolSucceeded e -> e.sessionId();
            case ToolFailed e -> e.sessionId();
            case StepEnded e -> e.sessionId();
            case StepFailed e -> e.sessionId();
            case PermissionAsked e -> e.sessionId();
            case ExecutionEnded e -> e.sessionId();
            case ExecutionStarted e -> e.sessionId();
            case StepStarted e -> e.sessionId();
            default -> null;
        };
        val identity = hierarchy.get(sid);
        if (identity == null) {
            return;
        }
        val name = identity.name();
        val path = identity.path();
        long now = System.currentTimeMillis();
        switch (event) {
            case TextDelta e -> {
                if (rootSessionId.equals(sid)) {
                    accounting.rootText.append(e.delta());
                }
                emit(new TextDeltaEvent(runId, sid, name, path, now, e.messageId(), e.ordinal(), e.delta()));
            }
            case ReasoningDelta e ->
                    emit(new ReasoningDeltaEvent(runId, sid, name, path, now, e.messageId(), e.ordinal(), e.delta()));
            case ToolStarted e -> tools.started(e, now);
            case ToolCalled e -> {
                val tool = tools.called(e, now);
                if ("subagent".equals(tool.rawName())) {
                    emit(new DelegationStartedEvent(runId, sid, name, path, now, "",
                            String.valueOf(e.input().getOrDefault("agent", "subagent")),
                            String.valueOf(e.input().getOrDefault("prompt", "")),
                            String.valueOf(e.input().getOrDefault("description", ""))));
                }
                if (!terminal.getAsBoolean()) {
                    emit(new ToolCallStartedEvent(runId, sid, name, path, now, e.callId(), tool.name(), tool.arguments()));
                }
            }
            case ToolSucceeded e -> toolFinished(sid, identity, e.callId(), e.output(), false, now);
            case ToolFailed e -> toolFinished(sid, identity, e.callId(), e.error(), true, now);
            case StepEnded e -> stepFinished(sid, identity, e.messageId(), e.finish(), e.finish(),
                    e.usage(), e.usage() != null ? e.usage().cost() : 0, now);
            case StepFailed e -> stepFinished(sid, identity, e.messageId(), "failed:" + e.errorType(),
                    "failed", e.usage(), 0, now);
            case PermissionAsked e -> emit(new PermissionRequestedEvent(runId, sid, name, path, now,
                    e.id(), e.action(), e.resources(), reply -> {
                        val decision = switch (reply.decision()) {
                            case ONCE -> PermissionDecision.ONCE;
                            case ALWAYS -> PermissionDecision.ALWAYS;
                            case REJECT -> PermissionDecision.REJECT;
                        };
                        client.permissions().reply(sid, e.id(), decision, reply.message());
                    }));
            case ExecutionStarted e -> {
                if (rootSessionId.equals(sid)) {
                    rootStarted = true;
                }
            }
            case StepStarted e -> emit(new StepStartedEvent(runId, sid, name, path, now, e.messageId(),
                    e.model() == null ? null : e.model().toString()));
            case ExecutionEnded e -> {
                if (!rootSessionId.equals(sid)) {
                    runningChildren.remove(sid);
                    return;
                }
                if (!rootStarted) {
                    // An execution this run never saw start belongs to an earlier turn, for example one that
                    // was cancelled locally while the server was still finishing it.
                    log.debug("Run {} ignores {} of an execution it did not start", runId, e.type());
                    return;
                }
                rootEnded.accept(RunEnd.of(e));
            }
            default -> { }
        }
    }

    /** The root agent's answer: the text recovered from history after a gap, otherwise the streamed text. */
    String text() {
        return reconciledText != null ? reconciledText : accounting.rootText.toString();
    }

    /** The identity of a session in this run's tree, or {@code null}. */
    RunSessionHierarchy.Identity identity(String sessionId) {
        return hierarchy.get(sessionId);
    }

    /** Replaces the streamed text with the authoritative text read from the session history. */
    void useReconciledText(String text) {
        reconciledText = text;
    }

    AgentResult result(RunEnd end, Integer maxSteps, Object typedResult) {
        boolean success = end.status() == RunStatus.SUCCEEDED;
        return AgentResult.builder(runId, rootSessionId)
                .status(end.status()).failureCategory(end.category()).retryAfterMs(end.retryAfterMs())
                .text(redactor.redact(text())).errorMessage(success ? null : redactor.redact(end.reason()))
                .typedResult(typedResult)
                .truncated(success && maxSteps != null && accounting.rootSteps >= maxSteps)
                .tokenUsage(accounting.usage).totalCost(accounting.cost)
                .toolExecutions(accounting.tools.stream().map(tool -> new ToolExecution(tool.callId(), tool.toolName(),
                        tool.agentPath(), redactor.redact(tool.arguments()), redactor.redact(tool.result()),
                        tool.isError(), tool.durationMs())).toList())
                .steps(accounting.steps).build();
    }

    /** Enforces {@code RunLimits.maxParallelChildren} by interrupting a child that exceeds it. */
    private void childStarted(String childSessionId) {
        runningChildren.add(childSessionId);
        Integer limit = spec.limits().maxParallelChildren();
        if (limit != null && limit > 0 && runningChildren.size() > limit) {
            log.warn("Run {} has {} sub-agent sessions running, above the limit of {}; interrupting {}",
                    runId, runningChildren.size(), limit, childSessionId);
            runningChildren.remove(childSessionId);
            Thread.ofVirtual().name("opencode-child-limit").start(() -> {
                try {
                    client.sessions().interrupt(childSessionId);
                } catch (RuntimeException e) {
                    log.warn("Could not interrupt sub-agent session {}: {}", childSessionId, e.getMessage());
                }
            });
        }
    }

    /** Steps and tool calls counted so far, for metrics. */
    int stepCount() {
        return accounting.steps.size();
    }

    int toolCallCount() {
        return accounting.tools.size();
    }

    io.opencode4j.runtime.TokenUsage usage() {
        return accounting.usage;
    }

    double cost() {
        return accounting.cost;
    }

    void emit(AgentEvent event) {
        if (listener != null) {
            try {
                listener.onEvent(EventRedaction.apply(event, redactor));
            } catch (Exception e) {
                log.warn("AgentEventListener threw an exception handling event {}: {}",
                        event.getClass().getSimpleName(), e.getMessage());
            }
        }
    }

    private void toolFinished(String sid, RunSessionHierarchy.Identity identity, String callId,
                              String output, boolean failed, long now) {
        val tool = tools.finished(sid, callId, now);
        accounting.tools.add(new ToolExecution(callId, tool.name(), identity.path(), tool.arguments(),
                output, failed, Math.max(0, now - tool.startTime())));
        if ("subagent".equals(tool.rawName())) {
            emit(new DelegationCompletedEvent(runId, sid, identity.name(), identity.path(), now,
                    "", tool.name(), !failed, output));
        }
        if (!terminal.getAsBoolean()) {
            emit(failed
                    ? new ToolCallFailedEvent(runId, sid, identity.name(), identity.path(), now, callId, tool.name(), output)
                    : new ToolCallCompletedEvent(runId, sid, identity.name(), identity.path(), now, callId, tool.name(), output));
        }
    }

    private void stepFinished(String sid, RunSessionHierarchy.Identity identity, String messageId,
                              String stepFinish, String eventFinish, io.opencode4j.model.TokenUsage usage,
                              double cost, long now) {
        val tokens = accounting.recordStep(identity.path(), stepFinish, usage, cost, rootSessionId.equals(sid));
        emit(new StepEndedEvent(runId, sid, identity.name(), identity.path(), now, messageId, eventFinish, tokens, cost));
        val limits = spec.limits();
        if (!terminal.getAsBoolean() && ((limits.maxCost() != null && accounting.cost >= limits.maxCost())
                || (limits.maxTokens() != null && accounting.usage.total() >= limits.maxTokens()))) {
            log.warn("Run {} exceeded budget limits; interrupting", runId);
            limitReached.accept(RunEnd.partial(FailureCategory.BUDGET, "Budget exhausted: tokens="
                    + accounting.usage.total() + ", cost=" + accounting.cost));
        }
    }
}
