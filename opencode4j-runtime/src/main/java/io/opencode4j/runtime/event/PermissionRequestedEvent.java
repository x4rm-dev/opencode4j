package io.opencode4j.runtime.event;

import java.util.List;
import java.util.function.Consumer;

/**
 * The agent asks for human or policy permission before executing a tool or action.
 */
public record PermissionRequestedEvent(
        String runId,
        String sessionId,
        String agentName,
        String agentPath,
        long timestamp,
        String requestId,
        String action,
        List<String> resources,
        Consumer<PermissionReply> responder
) implements AgentEvent {

    public enum Decision {
        ONCE,
        ALWAYS,
        REJECT
    }

    public record PermissionReply(Decision decision, String message) {
        public static PermissionReply allowOnce() {
            return new PermissionReply(Decision.ONCE, null);
        }

        public static PermissionReply allowAlways() {
            return new PermissionReply(Decision.ALWAYS, null);
        }

        public static PermissionReply reject(String message) {
            return new PermissionReply(Decision.REJECT, message);
        }
    }

    public void allowOnce() {
        if (responder != null) {
            responder.accept(PermissionReply.allowOnce());
        }
    }

    public void reject(String message) {
        if (responder != null) {
            responder.accept(PermissionReply.reject(message));
        }
    }
}
