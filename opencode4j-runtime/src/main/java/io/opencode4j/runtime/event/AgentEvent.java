package io.opencode4j.runtime.event;

/**
 * Base sealed interface for all domain events emitted during an agent run.
 *
 * <p>Every event carries the correlation runId, OpenCode sessionId, the specific agent name,
 * and the hierarchical path (e.g. {@code "boss"}, {@code "boss/lead"}, {@code "boss/lead/worker"}).</p>
 */
public sealed interface AgentEvent permits
        TextDeltaEvent,
        ReasoningDeltaEvent,
        ToolCallStartedEvent,
        ToolCallCompletedEvent,
        ToolCallFailedEvent,
        DelegationStartedEvent,
        DelegationCompletedEvent,
        StepStartedEvent,
        StepEndedEvent,
        PermissionRequestedEvent,
        RunStartedEvent,
        RunCompletedEvent,
        RunFailedEvent,
        RunInterruptedEvent {

    /** Unique identifier for the overall client run. */
    String runId();

    /** OpenCode session identifier where this event originated. */
    String sessionId();

    /** Name of the agent emitting this event. */
    String agentName();

    /** Hierarchical path of the agent (e.g. {@code "boss/clerk"}). */
    String agentPath();

    /** Epoch millisecond timestamp of the event. */
    long timestamp();
}
