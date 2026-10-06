package io.opencode4j.agent.runtime.run;

import io.opencode4j.client.OpenCodeClient;
import io.opencode4j.event.ExecutionEnded;
import io.opencode4j.event.ExecutionOutcome;
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
import java.util.Objects;
import java.util.function.BiConsumer;
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
    private final BiConsumer<ExecutionOutcome, String> finish;
    private final Runnable cancel;
    private final RunSessionHierarchy hierarchy;
    private final RunToolTracker tools = new RunToolTracker();
    private final RunAccounting accounting = new RunAccounting();

    RunEventProcessor(String runId, String rootSessionId, AgentSpec spec, OpenCodeClient client,
                      AgentEventListener listener, BooleanSupplier terminal,
                      BiConsumer<ExecutionOutcome, String> finish, Runnable cancel) {
        this.runId = Objects.requireNonNull(runId, "runId");
        this.rootSessionId = Objects.requireNonNull(rootSessionId, "rootSessionId");
        this.spec = Objects.requireNonNull(spec, "spec");
        this.client = Objects.requireNonNull(client, "client");
        this.listener = listener;
        this.terminal = Objects.requireNonNull(terminal, "terminal");
        this.finish = Objects.requireNonNull(finish, "finish");
        this.cancel = Objects.requireNonNull(cancel, "cancel");
        hierarchy = new RunSessionHierarchy(rootSessionId, spec.name());
    }

    void process(OpenCodeEvent event) {
        if (terminal.getAsBoolean()) {
            return;
        }
        if (event instanceof SessionCreated created) {
            hierarchy.register(created);
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
                        client.interactions().replyPermission(sid, e.id(), decision, reply.message());
                    }));
            case ExecutionEnded e -> {
                if (rootSessionId.equals(sid)) {
                    finish.accept(e.outcome(), e.detail());
                }
            }
            default -> { }
        }
    }

    String text() {
        return accounting.rootText.toString();
    }

    AgentResult result(boolean success, String reason, Integer maxSteps, Object typedResult) {
        return AgentResult.builder(runId, rootSessionId)
                .success(success).text(text()).errorMessage(success ? null : reason)
                .typedResult(typedResult)
                .truncated(success && maxSteps != null && accounting.rootSteps >= maxSteps)
                .tokenUsage(accounting.usage).totalCost(accounting.cost)
                .toolExecutions(accounting.tools).steps(accounting.steps).build();
    }

    void emit(AgentEvent event) {
        if (listener != null) {
            try {
                listener.onEvent(event);
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
            cancel.run();
        }
    }
}
