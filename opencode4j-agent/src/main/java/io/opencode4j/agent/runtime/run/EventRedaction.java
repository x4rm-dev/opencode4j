package io.opencode4j.agent.runtime.run;

import io.opencode4j.runtime.TextRedactor;
import io.opencode4j.runtime.event.AgentEvent;
import io.opencode4j.runtime.event.DelegationCompletedEvent;
import io.opencode4j.runtime.event.DelegationStartedEvent;
import io.opencode4j.runtime.event.PermissionRequestedEvent;
import io.opencode4j.runtime.event.ReasoningDeltaEvent;
import io.opencode4j.runtime.event.RunCompletedEvent;
import io.opencode4j.runtime.event.RunFailedEvent;
import io.opencode4j.runtime.event.RunInterruptedEvent;
import io.opencode4j.runtime.event.RunStartedEvent;
import io.opencode4j.runtime.event.StepEndedEvent;
import io.opencode4j.runtime.event.StepStartedEvent;
import io.opencode4j.runtime.event.TextDeltaEvent;
import io.opencode4j.runtime.event.ToolCallCompletedEvent;
import io.opencode4j.runtime.event.ToolCallFailedEvent;
import io.opencode4j.runtime.event.ToolCallStartedEvent;

/** Applies a {@link TextRedactor} to every free-text field of an {@link AgentEvent}. */
final class EventRedaction {

    private EventRedaction() {
    }

    static AgentEvent apply(AgentEvent event, TextRedactor r) {
        return switch (event) {
            case TextDeltaEvent e -> new TextDeltaEvent(e.runId(), e.sessionId(), e.agentName(), e.agentPath(),
                    e.timestamp(), e.messageId(), e.ordinal(), r.redact(e.delta()));
            case ReasoningDeltaEvent e -> new ReasoningDeltaEvent(e.runId(), e.sessionId(), e.agentName(), e.agentPath(),
                    e.timestamp(), e.messageId(), e.ordinal(), r.redact(e.delta()));
            case ToolCallStartedEvent e -> new ToolCallStartedEvent(e.runId(), e.sessionId(), e.agentName(),
                    e.agentPath(), e.timestamp(), e.callId(), e.toolName(), r.redact(e.arguments()));
            case ToolCallCompletedEvent e -> new ToolCallCompletedEvent(e.runId(), e.sessionId(), e.agentName(),
                    e.agentPath(), e.timestamp(), e.callId(), e.toolName(), r.redact(e.output()));
            case ToolCallFailedEvent e -> new ToolCallFailedEvent(e.runId(), e.sessionId(), e.agentName(),
                    e.agentPath(), e.timestamp(), e.callId(), e.toolName(), r.redact(e.error()));
            case DelegationStartedEvent e -> new DelegationStartedEvent(e.runId(), e.sessionId(), e.agentName(),
                    e.agentPath(), e.timestamp(), e.childSessionId(), e.targetAgentName(), r.redact(e.prompt()),
                    r.redact(e.description()));
            case DelegationCompletedEvent e -> new DelegationCompletedEvent(e.runId(), e.sessionId(), e.agentName(),
                    e.agentPath(), e.timestamp(), e.childSessionId(), e.targetAgentName(), e.success(),
                    r.redact(e.result()));
            case PermissionRequestedEvent e -> new PermissionRequestedEvent(e.runId(), e.sessionId(), e.agentName(),
                    e.agentPath(), e.timestamp(), e.requestId(), e.action(),
                    e.resources().stream().map(r::redact).toList(), e.responder());
            case RunStartedEvent e -> new RunStartedEvent(e.runId(), e.sessionId(), e.agentName(), e.agentPath(),
                    e.timestamp(), r.redact(e.prompt()));
            case RunCompletedEvent e -> new RunCompletedEvent(e.runId(), e.sessionId(), e.agentName(), e.agentPath(),
                    e.timestamp(), r.redact(e.finalText()));
            case RunFailedEvent e -> new RunFailedEvent(e.runId(), e.sessionId(), e.agentName(), e.agentPath(),
                    e.timestamp(), r.redact(e.error()));
            case RunInterruptedEvent e -> new RunInterruptedEvent(e.runId(), e.sessionId(), e.agentName(),
                    e.agentPath(), e.timestamp(), r.redact(e.reason()));
            case StepStartedEvent e -> e;
            case StepEndedEvent e -> e;
        };
    }
}
